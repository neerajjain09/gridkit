package com.medx.framework.type;

import static java.lang.String.format;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.medx.framework.dictionary.model.Dictionary;
import com.medx.framework.dictionary.model.TypeDescriptor;

public class TypeRegistryImpl implements TypeRegistry {
	private static final Logger log = LoggerFactory.getLogger(TypeRegistryImpl.class);
	
	private ConcurrentMap<Integer, TypeKey<?>>  typeKeyById = new ConcurrentHashMap<Integer, TypeKey<?>>();
	private ConcurrentMap<Class<?>, TypeKey<?>> typeKeyByClass = new ConcurrentHashMap<Class<?>, TypeKey<?>>();
	
	public TypeRegistryImpl(Dictionary... dictionaries) {
		for (Dictionary dictionary : dictionaries)
			loadDictionary(dictionary);
	}
	
	public synchronized void loadDictionary(Dictionary dictionary) {
		for (TypeDescriptor typeDescriptor : dictionary.getTypeDescriptors()) {
			if (typeKeyById.containsKey(typeDescriptor.getId())) {
				log.warn(format("Type with id '%d' already presented", typeDescriptor.getId()));
				continue;
			}
			
			Class<?> clazz = null;
			try {
				clazz = Class.forName(typeDescriptor.getClazz());
			} catch (ClassNotFoundException e) {
				log.warn(format("Failded to find class '%s'", typeDescriptor.getClazz()));
				continue;
			}
			
			if (typeKeyByClass.containsKey(clazz)) {
				log.warn(format("Type with class '%s' already presented", typeDescriptor.getClazz()));
				continue;
			}
			
			TypeKey<?> typeKey = new TypeKey<Object>(typeDescriptor.getId(), typeDescriptor.getVersion(), clazz);
			
			typeKeyById.put(typeDescriptor.getId(), typeKey);
			typeKeyByClass.put(clazz, typeKey);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> TypeKey<T> getTypeKey(int id) {
		return (TypeKey<T>)typeKeyById.get(id);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> TypeKey<T> getTypeKey(Class<T> clazz) {
		return (TypeKey<T>)typeKeyByClass.get(clazz);
	}
}
