/*******************************************************************************
 *     Cloud Foundry
 *     Copyright (c) [2009-2015] Pivotal Software, Inc. All Rights Reserved.
 *
 *     This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *     You may not use this product except in compliance with the License.
 *
 *     This product includes a number of subcomponents with
 *     separate copyright notices and license terms. Your use of these
 *     subcomponents is subject to the terms and conditions of the
 *     subcomponent's license, as noted in the LICENSE file.
 *******************************************************************************/
package org.cloudfoundry.identity.uaa.zone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cloudfoundry.identity.uaa.authentication.manager.DynamicLdapAuthenticationManager;
import org.cloudfoundry.identity.uaa.authentication.manager.LdapLoginAuthenticationManager;
import org.cloudfoundry.identity.uaa.ldap.LdapIdentityProviderDefinition;
import org.cloudfoundry.identity.uaa.scim.ScimGroupExternalMembershipManager;
import org.cloudfoundry.identity.uaa.scim.ScimGroupProvisioning;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.EXPECTATION_FAILED;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;

@RequestMapping("/identity-providers")
@RestController
public class IdentityProviderEndpoints {

    protected static Log logger = LogFactory.getLog(IdentityProviderEndpoints.class);

    private final IdentityProviderProvisioning identityProviderProvisioning;
    private final ScimGroupExternalMembershipManager scimGroupExternalMembershipManager;
    private final ScimGroupProvisioning scimGroupProvisioning;
    private final NoOpLdapLoginAuthenticationManager noOpManager = new NoOpLdapLoginAuthenticationManager();

    public IdentityProviderEndpoints(
        IdentityProviderProvisioning identityProviderProvisioning,
        ScimGroupExternalMembershipManager scimGroupExternalMembershipManager,
        ScimGroupProvisioning scimGroupProvisioning
    ) {
        this.identityProviderProvisioning = identityProviderProvisioning;
        this.scimGroupExternalMembershipManager = scimGroupExternalMembershipManager;
        this.scimGroupProvisioning = scimGroupProvisioning;
    }

    @RequestMapping(method = POST)
    public ResponseEntity<IdentityProvider> createIdentityProvider(@RequestBody IdentityProvider body) {
        body.setIdentityZoneId(IdentityZoneHolder.get().getId());
        IdentityProvider createdIdp = identityProviderProvisioning.create(body);
        return new ResponseEntity<>(createdIdp, HttpStatus.CREATED);
    }

    @RequestMapping(value = "{id}", method = PUT)
    public ResponseEntity<IdentityProvider> updateIdentityProvider(@PathVariable String id, @RequestBody IdentityProvider body) {
        body.setId(id);
        body.setIdentityZoneId(IdentityZoneHolder.get().getId());
        IdentityProvider updatedIdp = identityProviderProvisioning.update(body);
        return new ResponseEntity<>(updatedIdp, OK);
    }

    @RequestMapping(method = GET)
    public ResponseEntity<List<IdentityProvider>> retrieveIdentityProviders() {
        List<IdentityProvider> identityProviderList = identityProviderProvisioning.retrieveAll(false,IdentityZoneHolder.get().getId());
        return new ResponseEntity<>(identityProviderList, OK);
    }

    @RequestMapping(value = "test", method = POST)
    public ResponseEntity<Void> testIdentityProvider(@RequestBody IdentityProviderValidationRequest body) {
        HttpStatus status = OK;
        //create the LDAP IDP
        DynamicLdapAuthenticationManager manager = new DynamicLdapAuthenticationManager(
            body.getProvider().getConfigValue(LdapIdentityProviderDefinition.class),
            scimGroupExternalMembershipManager,
            scimGroupProvisioning,
            noOpManager
        );
        try {
            //attempt authentication
            Authentication result = manager.authenticate(body.getCredentials());
            if ((result == null) || (result != null && !result.isAuthenticated())) {
                status = EXPECTATION_FAILED;
            }
        } catch (BadCredentialsException x) {
            status = EXPECTATION_FAILED;
        } catch (InternalAuthenticationServiceException x) {
            status = BAD_REQUEST;
        } catch (Exception x) {
            logger.debug("Identity provider validation failed.", x);
            status = INTERNAL_SERVER_ERROR;
        }finally {
            //destroy IDP
            manager.destroy();
        }
        //return results
        return new ResponseEntity<>(status);
    }

    protected static class NoOpLdapLoginAuthenticationManager extends LdapLoginAuthenticationManager {
        @Override
        public Authentication authenticate(Authentication request) throws AuthenticationException {
            return request;
        }
    }
}
