package com.griddynamics.coherence.integration.spring.service;

/**
 * @author Dmitri Babaev
 */
public class ProxyServiceFactory extends ServiceFactory {

	public ServiceType getServiceType() {
		return ServiceType.Proxy;
	}
}