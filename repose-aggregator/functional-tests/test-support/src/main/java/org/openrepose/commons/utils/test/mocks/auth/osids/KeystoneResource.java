/*
 * _=_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=
 * Repose
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
 * Copyright (C) 2010 - 2015 Rackspace US, Inc.
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=_
 */
package org.openrepose.commons.utils.test.mocks.auth.osids;

import com.sun.jersey.spi.resource.Singleton;
import org.openrepose.commons.utils.StringUtilities;
import org.openrepose.commons.utils.test.mocks.BaseResource;
import org.openrepose.commons.utils.test.mocks.auth.osids.providers.KeystonePropertiesProvider;
import org.openrepose.commons.utils.test.mocks.auth.osids.providers.KeystoneProvider;
import org.openrepose.commons.utils.test.mocks.auth.osids.wrappers.JaxbElementWrapper;
import org.openrepose.commons.utils.test.mocks.auth.osids.wrappers.ResponseWrapper;
import org.openstack.docs.identity.api.v2.*;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.JAXBElement;
import javax.xml.datatype.DatatypeConfigurationException;
import java.io.IOException;

@Path("/keystone")
@Singleton
public class KeystoneResource extends BaseResource {

    private static final String DEFAULT_PROPS = "/keystone.properties";
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(KeystoneResource.class);

    public KeystoneResource() throws DatatypeConfigurationException, IOException {
        this(DEFAULT_PROPS);
    }

    public KeystoneResource(String propertiesFile) throws DatatypeConfigurationException, IOException {
        super(new KeystonePropertiesProvider(propertiesFile));
    }

    @POST
    @Path("/tokens")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response getToken(AuthenticationRequest authRequest, @Context UriInfo context) throws DatatypeConfigurationException {
        KeystoneProvider p = getProvider();
        ResponseWrapper wrapper = new JaxbElementWrapper();

        CredentialType credentialType = authRequest.getCredential().getValue();
        if (!validateCredentials(credentialType)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(wrapper.wrapElement(p.createUnauthorized("SecurityServiceFault: Unable to get customer auth by username"))).build();
        }

        PasswordCredentialsRequiredUsername credentials = (PasswordCredentialsRequiredUsername) credentialType;
        Token token = p.createToken(credentialType);

        AuthenticateResponse response = p.newAuthenticateResponse();
        response.setToken(token);
        response.setServiceCatalog(p.getServiceCatalog(credentials.getUsername()));
        response.setUser(p.getUser(credentials.getUsername()));

        return Response.ok(wrapper.wrapElement(response)).build();
    }

    @GET
    @Path("/tokens/{token}")
    @Produces(MediaType.APPLICATION_XML)
    public Response validateToken(@PathParam("token") String userToken, @HeaderParam("X-Auth-Token") String authToken, @QueryParam("belongsTo") String belongsTo, @Context UriInfo context) {
        KeystoneProvider p = getProvider();
        ResponseWrapper wrapper = new JaxbElementWrapper();

        if (!p.isValidToken(userToken)) {

            return Response.status(Response.Status.NOT_FOUND).entity(wrapper.wrapElement(p.createItemNotFound())).build();
        }

        if (!p.isValidToken(authToken)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(wrapper.wrapElement(p.createUnauthorized("No valid token provided. Please use the 'X-Auth-Token' header with a valid token."))).build();
        }

        String userName = p.getUsernameFromToken(userToken);

        if (!StringUtilities.isBlank(belongsTo) && !StringUtilities.nullSafeEquals(belongsTo, userName)) {
            return Response.status(Response.Status.NOT_FOUND).entity(wrapper.wrapElement(p.createItemNotFound())).build();
        }

        AuthenticateResponse response = p.newAuthenticateResponse();

        Token token = p.createToken(userToken);
        response.setToken(token);
        response.setUser(p.getUser(userName));

        if ("impersonated".equalsIgnoreCase(userName)) {
            response.getAny().add(getImpersonator("impersonator-id", "impersonator"));
        }

        return Response.ok(wrapper.wrapElement(response)).build();
    }

    private JAXBElement<UserForAuthenticateResponse> getImpersonator(String id, String name) {
        UserForAuthenticateResponse impersonator = new UserForAuthenticateResponse();
        impersonator.setId(id);
        impersonator.setName(name);
        com.rackspace.docs.identity.api.ext.rax_auth.v1.ObjectFactory factory = new com.rackspace.docs.identity.api.ext.rax_auth.v1.ObjectFactory();
        return factory.createImpersonator(impersonator);
    }

    @GET
    @Path("/users/{userId}/RAX-KSGRP")
    @Produces(MediaType.APPLICATION_XML)
    public Response getGroups(@PathParam("userId") String userId, @HeaderParam("X-Auth-Token") String authToken) {
        KeystoneProvider p = getProvider();
        ResponseWrapper wrapper = new JaxbElementWrapper();
        Integer id;

        try {
            id = Integer.valueOf(userId);
        } catch (NumberFormatException ex) {
            id = -1;
        }

        String userName = p.getUserName(id);

        if (!p.validateUser(userName)) {
            return Response.status(Response.Status.NOT_FOUND).entity(wrapper.wrapElement(p.createItemNotFound())).build();
        }

        if (!p.isValidToken(authToken)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(wrapper.wrapElement(p.createUnauthorized("No valid token provided. Please use the 'X-Auth-Token' header with a valid token."))).build();
        }

        return Response.ok(wrapper.wrapElement(p.getGroups(userName))).build();
    }

    protected boolean validateCredentials(CredentialType credentialType) {
        KeystoneProvider p = getProvider();
        PasswordCredentialsRequiredUsername credentials = (PasswordCredentialsRequiredUsername) credentialType;
        return p.validateUser(credentials.getUsername());
    }

    @GET
    @Path("/tokens/{tokenId}/endpoints")
    @Produces(MediaType.APPLICATION_XML)
    public Response getEndpoints(@PathParam("tokenId") String tokenId, @HeaderParam("X-Auth-Token") String authToken) {

        KeystoneProvider p = getProvider();
        ResponseWrapper wrapper = new JaxbElementWrapper();

        if (!p.isValidToken(tokenId)) {

            return Response.status(Response.Status.NOT_FOUND).entity(wrapper.wrapElement(p.createItemNotFound())).build();
        }

        if (!p.isValidToken(authToken)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(wrapper.wrapElement(p.createUnauthorized("No valid token provided. Please use the 'X-Auth-Token' header with a valid token."))).build();
        }
        EndpointList endpoints = p.getEndpoints(tokenId);
        return Response.ok(wrapper.wrapElement(endpoints)).build();
    }
}
