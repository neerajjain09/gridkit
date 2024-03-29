/**
 * Copyright 2008-2009 Grid Dynamics Consulting Services, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.gridkit.fabric.remoting;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import org.apache.log4j.Logger;

public class RmiChannel {

    private static final Logger logger = Logger.getLogger(RmiChannel.class);

    private static AtomicLong callId = new AtomicLong(0L);

    private final OutputChannel messageOut;
    private final Executor callDispatcher;


    private final Map<Object, RemoteInstance> object2remote = new IdentityHashMap<Object, RemoteInstance>();
    private final Map<RemoteInstance, Object> remote2object = new HashMap<RemoteInstance, Object>();

    private final Map<RemoteInstance, Object> remoteInstanceProxys = new ConcurrentHashMap<RemoteInstance, Object>();
    private final Map<Long, RemoteCallContext> remoteReturnWaiters = new ConcurrentHashMap<Long, RemoteCallContext>();

    @SuppressWarnings("unchecked")
    private final Map<Class<?>, Class[]> remoteAutodetectCache = new ConcurrentHashMap<Class<?>, Class[]>();
    private final Map<RemoteMethodSignature, Method> methodCache = new ConcurrentHashMap<RemoteMethodSignature, Method>();
    private final Set<Class<?>> remoteInterfaceMarkers;

    private final Map<String, Object> name2bean = new ConcurrentHashMap<String, Object>();
    private final Map<Object, String> bean2name = new ConcurrentHashMap<Object, String>();

    private volatile boolean terminated = false;

    public RmiChannel(OutputChannel output, Executor callDispatcher, Class<?>[] remoteInterfaceMarkes) {
        this.messageOut = output;
        this.callDispatcher = callDispatcher;
        this.remoteInterfaceMarkers = new HashSet<Class<?>>(Arrays.asList(remoteInterfaceMarkes));
    }

    public void registerNamedBean(String name, Object obj) {
        name2bean.put(name, obj);
        bean2name.put(obj, name);
    }

    public void handleRemoteMessage(RemoteMessage message) {
        if (message instanceof RemoteCall) {

            final RemoteCall remoteCall = (RemoteCall) message;
            if (remoteCall.getArgs() != null) {
                for (int n = 0; n < remoteCall.getArgs().length; n++) {
                    Object arg = remoteCall.getArgs()[n];
                    if (arg instanceof RemoteInstance) {
                        RemoteInstance remoteInstance = (RemoteInstance) arg;
                        remoteCall.getArgs()[n] = getProxyFromRemoteInstance(remoteInstance);
                    }
                }
            }

            Runnable runnable = new Runnable() {
                public void run() {
                    String threadName = Thread.currentThread().getName();
                    Thread.currentThread().setName("RemoteCall: " + remoteCall.toString());

                    try {
                        RemoteReturn remoteReturn;
                        try {
                            remoteReturn = delegateCall(remoteCall);
                        } catch (Exception e) {
                            e.printStackTrace();
                            RmiChannel.this.close();
                            return;
                        }
                        try {
                            sendMessage(remoteReturn);
                        } catch (IOException e) {
                            RmiChannel.this.close();
                        }
                    }
                    finally {
                        Thread.currentThread().setName(threadName);
                    }
                }
            };

            callDispatcher.execute(runnable);

        } else if (message instanceof RemoteReturn) {
            RemoteReturn remoteReturn = (RemoteReturn) message;
            long id = remoteReturn.getCallId();
            RemoteCallContext context = remoteReturnWaiters.get(id);
            if (context == null) {
                throw new RuntimeException("Orphaned remote return: " + remoteReturn);
            }
            context.result = remoteReturn;
            LockSupport.unpark(context.thread);
        } else {
            throw new RuntimeException("Unknown RemoteMessage type. " + message); //$NON-NLS-1$
        }
    }

    public synchronized void close() {
        // TODO global synchronization somehow
        if (terminated) {
            return;
        }
        terminated = true;

        synchronized (object2remote) {
            object2remote.clear();
            remote2object.clear();
        }
        remoteInstanceProxys.clear();
        for (RemoteCallContext context : remoteReturnWaiters.values()) {
            if (context.result == null) {
                context.result = new RemoteReturn(true, new RemoteException("Connection closed"), 0);
                LockSupport.unpark(context.thread);
            }
        }

        remoteReturnWaiters.clear();
    }

    protected void sendMessage(RemoteMessage message) throws IOException {
        messageOut.send(message);
    }

    protected RemoteReturn delegateCall(RemoteCall remoteCall) {

        RemoteInstance instance = remoteCall.getRemoteInstance();
        RemoteMethodSignature methodId = remoteCall.getMethod();
        long callId = remoteCall.getCallId();

        Object implementator;
        synchronized (object2remote) {
            implementator = remote2object.get(remoteCall.getRemoteInstance());
        }

        if (implementator == null) {
            return new RemoteReturn(true, new RemoteException(String.format("Instance %s has not been exported ", instance)), callId);
        }

        RemoteReturn remoteReturn;

        Method implementationMethod;
        try {
            implementationMethod = lookupMethod(methodId);
        } catch (Exception e) {
            return new RemoteReturn(true, new RemoteException(String.format("Method cannot be resolved ", methodId)), callId);
        }

        Object methodReturn = null;
        try {
            methodReturn = implementationMethod.invoke(implementator, remoteCall.getArgs());
            remoteReturn = new RemoteReturn(false, methodReturn, callId);
        } catch (InvocationTargetException e) {
            System.err.println("Call[" + remoteCall + "] exception " + e.getCause().toString());
            remoteReturn = new RemoteReturn(true, e.getCause(), callId);
        } catch (Exception e) {
            remoteReturn = new RemoteReturn(true, new RemoteException("Invocation failed", e), callId);
        }

        return remoteReturn;
    }

    @SuppressWarnings("unchecked")
    private Method lookupMethod(RemoteMethodSignature methodSig) throws ClassNotFoundException, SecurityException, NoSuchMethodException {

        Method method = methodCache.get(methodSig);
        if (method != null) {
            return method;
        } else {
            Class iface = classForName(methodSig.getClassName());
            String methodName = methodSig.getMethodName();
            Class[] argTypes = toClassObjects(methodSig.getMethodSignature());
            method = iface.getMethod(methodName, argTypes);
            method.setAccessible(true);
            methodCache.put(methodSig, method);
            return method;
        }
    }

    protected Object remoteInvocation(final RemoteStub stub, final Object proxy, final Method method, final Object[] args) throws Throwable {


        Long id = callId.getAndIncrement();
        RemoteInstance remoteInstance = stub.getRemoteInstance();
        RemoteMessage remoteCall = new RemoteCall(remoteInstance, new RemoteMethodSignature(method), args, id);

        RemoteCallContext context = new RemoteCallContext(Thread.currentThread());

        if (terminated) {
            throw new RemoteException("Connection closed");
        }
        // TODO race condition on close
        remoteReturnWaiters.put(id, context);
        try {
            sendMessage(remoteCall);
        }
        catch (IOException e) {
            throw new RemoteException("Call failed", e);
        }

        while (true) {
            synchronized (this) {
                if (terminated) {
                    throw new InterruptedException("Terminated");
                }
            }
            LockSupport.park();
            if (context.result != null) {
                break;
            } else if (terminated) {
                throw new RemoteException("Connection closed");
            } else if (Thread.interrupted()) {
                // TODO handle interruption
                throw new InterruptedException();
            }
        }

        remoteReturnWaiters.remove(id);
        RemoteReturn ret = context.result;

        if (ret.throwing) {
            throw (Throwable) ret.getRet();
        }

        return ret.getRet();
    }

    private Object getProxyFromRemoteInstance(RemoteInstance remoteInstance) {
        Object proxy = remoteInstanceProxys.get(remoteInstance);
        if (proxy == null) {
            try {
                proxy = RemoteStub.buildProxy(remoteInstance, this);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            remoteInstanceProxys.put(remoteInstance, proxy);
        }
        return proxy;
    }

    public <T> void exportObject(Class<T> iface, T implementation) {
        exportObject(new Class[]{iface}, implementation);
    }

    @SuppressWarnings("unchecked")
    private RemoteInstance exportObject(Class[] interfaces, Object obj) {
        synchronized (object2remote) {
            RemoteInstance remote = object2remote.get(obj);
            if (remote == null) {
                String uuid = UUID.randomUUID().toString();
                String[] ifNames = new String[interfaces.length];
                for (int i = 0; i != ifNames.length; ++i) {
                    ifNames[i] = interfaces[i].getName();
                }
                remote = new RemoteInstance(uuid, ifNames);
                object2remote.put(obj, remote);
                remote2object.put(remote, obj);
            }
            return remote;
        }
    }

    public Object streamResolveObject(Object obj) {
        if (obj instanceof BeanRef) {
            BeanRef ref = (BeanRef) obj;
            Object bean = name2bean.get(ref.getBeanName());
            if (bean == null) {
                logger.warn("Cannot resolve bean named '" + ref + "'");
            }
            return bean;
        }
        if (obj instanceof RemoteRef) {
            return getProxyFromRemoteInstance(((RemoteRef) obj).getIdentity());
        } else {
            return obj;
        }
    }

    public Object streamReplaceObject(Object obj) {
        if (bean2name.containsKey(obj)) {
            return new BeanRef(bean2name.get(obj));
        }
        if (!(obj instanceof Serializable) || (obj instanceof Proxy)) {
            // allow explicit export
            synchronized (object2remote) {
                RemoteInstance id = object2remote.get(obj);
                if (id != null) {
                    return new RemoteRef(id);
                }
            }
            Class<?>[] remoteIf = detectRemoteInterfaces(obj);
            if (remoteIf.length > 0) {
                return new RemoteRef(exportObject(remoteIf, obj));
            }
        }

        return obj;
    }

    @SuppressWarnings("unchecked")
    protected Class<?>[] detectRemoteInterfaces(Object obj) {
        Class<?> objClass = obj.getClass();
        Class<?>[] result = remoteAutodetectCache.get(objClass);
        if (result != null) {
            return result;
        } else {
            List<Class> iflist = new ArrayList<Class>();
            iflist.addAll(Arrays.asList(objClass.getInterfaces()));

            Iterator<Class> it = iflist.iterator();
            while (it.hasNext()) {
                Class intf = it.next();
                boolean remote = false;
                for (Class<?> marker : remoteInterfaceMarkers) {
                    if (marker.isAssignableFrom(intf)) {
                        remote = true;
                        break;
                    }
                }

                if (!remote) {
                    it.remove();
                    continue;
                }

                for (Class other : iflist) {
                    if (intf != other && intf.isAssignableFrom(other)) {
                        it.remove();
                    }
                }
            }

            result = iflist.toArray(new Class[iflist.size()]);
            remoteAutodetectCache.put(objClass, result);

            return result;
        }
    }

    @SuppressWarnings("unchecked")
    public static String[] toClassNames(Class[] classes) {
        String[] names = new String[classes.length];
        for (int i = 0; i != classes.length; ++i) {
            names[i] = classes[i].getName();
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    public Class[] toClassObjects(String[] names) throws ClassNotFoundException {
        Class[] classes = new Class[names.length];
        for (int i = 0; i != names.length; ++i) {
            classes[i] = classForName(names[i]);
        }
        return classes;
    }

    @SuppressWarnings("unchecked")
    public Class classForName(String className) throws ClassNotFoundException {
        return Class.forName(className);
    }

    public ClassLoader getClassLoader() {
        return this.getClass().getClassLoader();
    }

    public interface OutputChannel {
        public void send(RemoteMessage message) throws IOException;
    }

    private static class RemoteCallContext {
        public final Thread thread;
        public RemoteReturn result;

        public RemoteCallContext(Thread thread) {
            this.thread = thread;
        }
    }
}
