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
package org.openrepose.filters.clientauth.atomfeed.sax;

// Retrieves admin tokens from os auth for the atom feed reader
// TODO: this should be refactored into the openstack stuff I think
// TODO: also I wish JSON instead of sax and xml and marshalling :(

import org.openrepose.common.auth.AuthServiceException;
import org.openrepose.common.auth.ResponseUnmarshaller;
import org.openrepose.common.auth.openstack.AdminToken;
import org.openrepose.commons.utils.http.ServiceClientResponse;
import org.openrepose.commons.utils.transform.jaxb.JaxbEntityToXml;
import org.openrepose.core.services.serviceclient.akka.AkkaServiceClient;
import org.openrepose.core.services.serviceclient.akka.AkkaServiceClientException;
import org.openstack.docs.identity.api.v2.*;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import java.util.HashMap;

@Deprecated
public class AdminTokenProvider {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(AdminTokenProvider.class);
    private final String requestBody;
    private String authUrl;
    private AkkaServiceClient client;
    private JAXBContext coreJaxbContext;
    private ResponseUnmarshaller marshaller;
    private AdminToken curAdminToken;

    public AdminTokenProvider(AkkaServiceClient client, String authUrl, String username, String password) {
        this.client = client;
        this.authUrl = authUrl;
        setJAXBContext();
        marshaller = new ResponseUnmarshaller(coreJaxbContext);
        ObjectFactory factory = new ObjectFactory();

        PasswordCredentialsRequiredUsername credentials = new PasswordCredentialsRequiredUsername();
        credentials.setUsername(username);
        credentials.setPassword(password);

        JAXBElement jaxbCredentials = factory.createPasswordCredentials(credentials);

        AuthenticationRequest request = new AuthenticationRequest();
        request.setCredential(jaxbCredentials);

        JaxbEntityToXml jaxbToString = new JaxbEntityToXml(coreJaxbContext);
        requestBody = jaxbToString.transform(factory.createAuth(request));
    }

    private void setJAXBContext() {
        try {
            coreJaxbContext = JAXBContext.newInstance(
                    org.openstack.docs.identity.api.v2.ObjectFactory.class,
                    com.rackspace.docs.identity.api.ext.rax_auth.v1.ObjectFactory.class);
        } catch (JAXBException ex) {
            LOG.trace("Unable to set JAXB Context", ex);
        }
    }

    public String getFreshAdminToken() throws AuthServiceException {
        curAdminToken = null;
        return getAdminToken();
    }

    public String getAdminToken() throws AuthServiceException {

        String adminToken = curAdminToken != null && curAdminToken.isValid() ? curAdminToken.getToken() : null;

        if (adminToken == null) {
            final ServiceClientResponse serviceResponse;
            try {
                serviceResponse = client.post(AdminToken.CACHE_KEY,
                        authUrl + "/tokens",
                        new HashMap<String, String>(),
                        requestBody,
                        MediaType.APPLICATION_XML_TYPE);
            } catch (AkkaServiceClientException e) {
                throw new AuthServiceException("Unable to get admin token.", e);
            }

            switch (serviceResponse.getStatus()) {
                case HttpServletResponse.SC_OK:
                    final AuthenticateResponse authenticateResponse = marshaller.unmarshall(serviceResponse.getData(), AuthenticateResponse.class);

                    Token token = authenticateResponse.getToken();
                    curAdminToken = new AdminToken(token.getId(), token.getExpires().toGregorianCalendar());
                    adminToken = curAdminToken.getToken();
                    break;

                default:
                    LOG.error("Unable to get admin token.  Verify admin credentials. " + serviceResponse.getStatus());
                    curAdminToken = null;
                    break;
            }
        }

        return adminToken;
    }
}
