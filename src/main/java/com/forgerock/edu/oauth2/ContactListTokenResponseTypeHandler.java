package com.forgerock.edu.oauth2;

import com.forgerock.edu.policy.ContactListPrivilegesEvaluator;
import com.forgerock.edu.util.OAuth2Util;
import com.iplanet.sso.SSOException;
import com.iplanet.sso.SSOToken;
import com.sun.identity.entitlement.EntitlementException;
import com.sun.identity.shared.debug.Debug;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.forgerock.oauth2.core.*;
import org.forgerock.openam.oauth2.OAuth2Constants;
import static org.forgerock.openam.oauth2.OAuth2Constants.AuthorizationEndpoint.TOKEN;
import static org.forgerock.openam.oauth2.OAuth2Constants.Params.ACCESS_TOKEN;

import org.forgerock.oauth2.core.exceptions.InvalidClientException;
import org.forgerock.oauth2.core.exceptions.NotFoundException;
import org.forgerock.oauth2.core.exceptions.ServerException;
import org.forgerock.openam.oauth2.token.TokenStore;
import org.forgerock.openam.oauth2.token.grantset.GrantSet;
import org.json.JSONException;

/**
 * This ResponseTypeHandler implementation handles the response type named
 * "token" in a ContactList specific way. The purpose of this handler is to
 * capture the user's ContactList specific privileges in the requested claim set
 * in the token before the token is stored in the {@link TokenStore}. This
 * implementation The {@link #handle(java.lang.String, java.util.Set, org.forgerock.oauth2.core.ResourceOwner, java.lang.String, java.lang.String, java.lang.String, org.forgerock.oauth2.core.OAuth2Request, java.lang.String, java.lang.String)
 * }
 * method is basically copied from the
 * {@link TokenResponseTypeHandler#handle(java.lang.String, java.util.Set, org.forgerock.oauth2.core.ResourceOwner, java.lang.String, java.lang.String, java.lang.String, org.forgerock.oauth2.core.OAuth2Request, java.lang.String, java.lang.String) TokenResponseTypeHandler's handle method}.
 * The only difference is that this handler calculates the ContactList specific
 * privilege set (see
 * {@link #addContactListPrivilegesAsClaim(org.forgerock.oauth2.core.OAuth2Request, java.lang.String)}) and
 * places it into the requested claim set as a claim with hard-coded values.
 *
 * @author vrg
 */
@Singleton
public class ContactListTokenResponseTypeHandler implements ResponseTypeHandler {

    private static final Debug DEBUG = Debug.getInstance("ContactListTokenResponseTypeHandler");
    private final TokenStore tokenStore;

    @Inject
    public ContactListTokenResponseTypeHandler(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map.Entry<String, Token> handle(String tokenType, Set<String> scope, ResourceOwner resourceOwner, String clientId, String redirectUri, String nonce, OAuth2Request request, String codeChallenge, String codeChallengeMethod) throws InvalidClientException, ServerException, NotFoundException {
        String claims = null;
        //only pass the claims param if this is a request to the authorize endpoint
        if (request.getParameter(OAuth2Constants.Params.CODE) == null) {
            claims = request.getParameter(OAuth2Constants.Custom.CLAIMS);
            claims = addContactListPrivilegesAsClaim(request, claims);
        }
        // TODO: Investigate and fix the next two lines.
        GrantSet grantSet = this.tokenStore.getGrantSet(clientId, resourceOwner.getId(), request, true);
        Grant grant = this.tokenStore.createGrant(request, grantSet, clientId);
                   //this.tokenStore.createGrant(clientId, resourceOwner.getId(), scope, request);
        AccessToken generatedAccessToken = this.tokenStore.createAccessToken(grantSet, grant, tokenType, redirectUri, nonce, claims, request, resourceOwner.getAuthTime(), scope, resourceOwner.getAuthLevel());
                                         //this.tokenStore.createAccessToken(grant, "token", tokenType, redirectUri, nonce, claims, request, resourceOwner.getAuthTime(), scope, resourceOwner.getAuthLevel());
        return new AbstractMap.SimpleEntry("access_token", generatedAccessToken);
    }

    /**
     * Evaluates the user's privileges and adds it into the claims structure.
     * @param request The current OAuth2Request
     * @param claims The initial claims JSON structure as a String.
     * @return The modified claims JSON as a String.
     */
    String addContactListPrivilegesAsClaim(OAuth2Request request, String claims) {
        try {
            DEBUG.message("Original claims string: " + claims);
            claims = request.getParameter(OAuth2Constants.Custom.CLAIMS);
            Set<String> privileges = ContactListPrivilegesEvaluator.evaluatePrivileges(request);
            claims = Claims.parse(claims)
                    //DONE lab05_01: Put a new claim definition named "contactlist-privileges" into the "userinfo" branch - this is needed when the userinfo endpoint is used.
                    //DONE lab05_01: Provide the user's privilege set as the hard-coded value for the "contactlist-privileges" claim.
                    //DONE lab05_01: Place the same claim definition to the "id_token" branch  - this is relevant during the id_token generation.
                    //DONE lab05_01: Hint: use the setClaimValues method of the Claims class to add
                    .setClaimValues("userinfo", "contactlist-privileges", privileges)
                    .setClaimValues("id_token", "contactlist-privileges", privileges)
                    .toString();
            DEBUG.message("Replaced claims string: " + claims);
        } catch (SSOException | EntitlementException | JSONException ex) {
            DEBUG.error("Error during extending claims: ", ex);
        }
        return claims;
    }

    @Override
    public OAuth2Constants.UrlLocation getReturnLocation() {
        return OAuth2Constants.UrlLocation.FRAGMENT;
    }

}
