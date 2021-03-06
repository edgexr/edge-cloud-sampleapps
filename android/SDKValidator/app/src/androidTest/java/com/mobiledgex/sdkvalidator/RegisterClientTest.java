/**
 * Copyright 2018-2020 MobiledgeX, Inc. All rights and licenses reserved.
 * MobiledgeX, Inc. 156 2nd Street #408, San Francisco, CA 94105
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

package com.mobiledgex.sdkvalidator;

import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.auth0.android.jwt.Claim;
import com.auth0.android.jwt.DecodeException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mobiledgex.matchingengine.DmeDnsException;
import com.mobiledgex.matchingengine.MatchingEngine;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import com.auth0.android.jwt.JWT;

import distributed_match_engine.AppClient;
import io.grpc.StatusRuntimeException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class RegisterClientTest {
    public static final String TAG = "EngineCallTest";
    public static final long GRPC_TIMEOUT_MS = 21000;

    public static final String organizationName = "MobiledgeX";
    // Other globals:
    public static final String applicationName = "MobiledgeX SDK Demo";
    public static final String appVersion = "2.0";

    FusedLocationProviderClient fusedLocationClient;

    public static String hostOverride = "wifi.dme.mobiledgex.net";
    public static int portOverride = 50051;
    public static String findCloudletCarrierOverride = "GDDT"; // Allow "Any" if using "", but this likely breaks test cases.

    public boolean useHostOverride = true;

    // "useWifiOnly = true" also disables network switching, since the android default is WiFi.
    // Must be set to true if you are running tests without a SIM card.
    public boolean useWifiOnly = true;

    private int getCellId(Context context, MatchingEngine me) {
        int cellId = 0;
        List<Pair<String, Long>> cellIdList = me.retrieveCellId(context);
        if (cellIdList != null && cellIdList.size() > 0) {
            cellId = cellIdList.get(0).second.intValue();
        }
        return cellId;
    }

    private Location getTestLocation(double latitude, double longitude) {
        Location location = new Location("MobiledgeX_Test");
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        return location;
    }

    @Before
    public void LooperEnsure() {
        // SubscriberManager needs a thread. Start one:
        if (Looper.myLooper()==null)
            Looper.prepare();
    }

    @Before
    public void grantPermissions() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
            uiAutomation.grantRuntimePermission(
                    InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName(),
                    "android.permission.READ_PHONE_STATE");
            uiAutomation.grantRuntimePermission(
                    InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName(),
                    "android.permission.ACCESS_COARSE_LOCATION");
            uiAutomation.grantRuntimePermission(
                    InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName(),
                    "android.permission.ACCESS_FINE_LOCATION"
            );
        }
    }

    @Test
    public void mexDisabledTest() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MatchingEngine me = new MatchingEngine(context);
        me.setUseWifiOnly(useWifiOnly);
        me.setMatchingEngineLocationAllowed(false);
        me.setAllowSwitchIfNoSubscriberInfo(true);

        try {
            Location location = getTestLocation( 47.6062,122.3321);

            AppClient.RegisterClientRequest registerClientRequest = me.createDefaultRegisterClientRequest(context, organizationName).build();
            assertTrue(registerClientRequest == null);

            AppClient.FindCloudletRequest findCloudletRequest;
            findCloudletRequest = me.createDefaultFindCloudletRequest(context, location)
                .setCarrierName(findCloudletCarrierOverride)
                .build();
            assertTrue(findCloudletRequest == null);

            AppClient.GetLocationRequest locationRequest = me.createDefaultGetLocationRequest(context).build();
            assertTrue(locationRequest == null);

            AppClient.VerifyLocationRequest verifyLocationRequest = me.createDefaultVerifyLocationRequest(context, location).build();
            assertTrue(verifyLocationRequest == null);

            AppClient.AppInstListRequest appInstListRequest = me.createDefaultAppInstListRequest(context, location).build();
            assertTrue(appInstListRequest == null);

        } catch (PackageManager.NameNotFoundException nnfe) {
            Log.e(TAG, Log.getStackTraceString(nnfe));
            assertFalse("mexDisabledTest: NameNotFoundException", true);
        } catch (StatusRuntimeException sre) {
            Log.e(TAG, Log.getStackTraceString(sre));
            assertFalse("mexDisabledTest: StatusRuntimeException!", true);
        }  catch (Exception e) {
            Log.e(TAG, "Creation of request is not supposed to succeed!");
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    @Test
    public void registerClientTest() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MatchingEngine me = new MatchingEngine(context);
        me.setUseWifiOnly(useWifiOnly);
        me.setMatchingEngineLocationAllowed(true);
        me.setAllowSwitchIfNoSubscriberInfo(true);

        AppClient.RegisterClientReply reply = null;
        String appName = applicationName;


        try {
            Location location = getTestLocation( 47.6062,122.3321);

            AppClient.RegisterClientRequest request = me.createDefaultRegisterClientRequest(context, organizationName)
                    .setAppName(applicationName)
                    .setAppVers(appVersion)
                    .setCellId(getCellId(context, me))
                    .build();
            if (useHostOverride) {
                reply = me.registerClient(request, hostOverride, portOverride, GRPC_TIMEOUT_MS);
            } else {
                reply = me.registerClient(request, me.generateDmeHostAddress(), me.getPort(), GRPC_TIMEOUT_MS);
            }

            JWT jwt = null;
            try {
                jwt = new JWT(reply.getSessionCookie());
            } catch (DecodeException e) {
                Log.e(TAG, Log.getStackTraceString(e));
                assertFalse("registerClientTest: DecodeException!", true);
            }

            // Validate JWT
            boolean isExpired = jwt.isExpired(10); // 10 seconds leeway
            assertTrue(!isExpired);
            Log.i(TAG, "Claims count: "+jwt.getClaims().keySet().size());
            for (String key: jwt.getClaims().keySet()) {
                Claim claim = jwt.getClaims().get(key);
                Log.i(TAG, "key: "+key+" Claim: "+claim.asString());
            }

            Claim c = jwt.getClaim("key");
            JsonObject claimJson = c.asObject(JsonObject.class);
            String orgName = claimJson.get("orgname").getAsString();
            assertEquals("orgname doesn't match!", "MobiledgeX", orgName);

            Log.i(TAG, "registerReply.getSessionCookie()="+reply.getSessionCookie());
            assertTrue(reply != null);
            assertTrue(reply.getStatus() == AppClient.ReplyStatus.RS_SUCCESS);
            assertTrue( !reply.getUniqueId().isEmpty());
            assertTrue( reply.getSessionCookie().length() > 0);
        } catch (PackageManager.NameNotFoundException nnfe) {
            Log.e(TAG, Log.getStackTraceString(nnfe));
            assertFalse("ExecutionException registering using PackageManager.", true);
        } catch (DmeDnsException dde) {
            Log.e(TAG, Log.getStackTraceString(dde));
            assertFalse("registerClientTest: DmeDnsException!", true);
        } catch (ExecutionException ee) {
            Log.e(TAG, Log.getStackTraceString(ee));
            assertFalse("registerClientTest: ExecutionException!", true);
        } catch (StatusRuntimeException sre) {
            Log.e(TAG, Log.getStackTraceString(sre));
            assertFalse("registerClientTest: StatusRuntimeException!", true);
        } catch (InterruptedException ie) {
            Log.e(TAG, Log.getStackTraceString(ie));
            assertFalse("registerClientTest: InterruptedException!", true);
        }

        Log.i(TAG, "registerClientTest reply: " + reply.toString());
        assertEquals(0, reply.getVer());
        assertEquals(AppClient.ReplyStatus.RS_SUCCESS, reply.getStatus());
    }

    @Test
    public void registerClientEmptyAppVersion() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MatchingEngine me = new MatchingEngine(context);
        me.setUseWifiOnly(useWifiOnly);
        me.setMatchingEngineLocationAllowed(true);
        me.setAllowSwitchIfNoSubscriberInfo(true);

        AppClient.RegisterClientReply reply = null;

        try {
            AppClient.RegisterClientRequest request = me.createDefaultRegisterClientRequest(context, organizationName)
                    .setAppName(applicationName)
                    .setAppVers("")
                    .setCellId(getCellId(context, me))
                    .build();
            if (useHostOverride) {
                reply = me.registerClient(request, hostOverride, portOverride, GRPC_TIMEOUT_MS);
            } else {
                reply = me.registerClient(request, me.generateDmeHostAddress(), me.getPort(), GRPC_TIMEOUT_MS);
            }

        } catch (PackageManager.NameNotFoundException nnfe) {
            Log.e(TAG, Log.getStackTraceString(nnfe));
            assertFalse("ExecutionException registering using PackageManager.", true);
        } catch (DmeDnsException dde) {
            Log.e(TAG, Log.getStackTraceString(dde));
            assertFalse("registerClientTest: DmeDnsException!", true);
        } catch (ExecutionException ee) {
            Log.e(TAG, Log.getStackTraceString(ee));
            assertFalse("registerClientTest: ExecutionException!", true);
        } catch (StatusRuntimeException sre) {
            Log.e(TAG, Log.getStackTraceString(sre));
            // This is expected when AppVers is empty.
            assertEquals("INVALID_ARGUMENT: AppVers cannot be empty", sre.getLocalizedMessage());
        } catch (InterruptedException ie) {
            Log.e(TAG, Log.getStackTraceString(ie));
            assertFalse("registerClientTest: InterruptedException!", true);
        }
    }

    @Test
    public void registerClientEmptyOrgName() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MatchingEngine me = new MatchingEngine(context);
        me.setUseWifiOnly(useWifiOnly);
        me.setMatchingEngineLocationAllowed(true);
        me.setAllowSwitchIfNoSubscriberInfo(true);

        AppClient.RegisterClientReply reply = null;

        try {
            Location location = getTestLocation( 47.6062,122.3321);

            AppClient.RegisterClientRequest request = me.createDefaultRegisterClientRequest(context, "")
                    .setAppName(applicationName)
                    .setAppVers(appVersion)
                    .setCellId(getCellId(context, me))
                    .build();
        } catch (PackageManager.NameNotFoundException nnfe) {
            Log.e(TAG, Log.getStackTraceString(nnfe));
            assertFalse("ExecutionException registering using PackageManager.", true);
        } catch (StatusRuntimeException sre) {
            Log.e(TAG, Log.getStackTraceString(sre));
            assertFalse("registerClientTest: StatusRuntimeException!", true);
        } catch (IllegalArgumentException iae) {
            Log.e(TAG, Log.getStackTraceString(iae));
            // This is expected when OrgName is empty.
            assertEquals("RegisterClientRequest requires a organization name.", iae.getLocalizedMessage());
        }
    }

    @Test
    public void registerClientBadAppName() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MatchingEngine me = new MatchingEngine(context);
        me.setUseWifiOnly(useWifiOnly);
        me.setMatchingEngineLocationAllowed(true);
        me.setAllowSwitchIfNoSubscriberInfo(true);

        AppClient.RegisterClientReply reply = null;

        try {
            AppClient.RegisterClientRequest request = me.createDefaultRegisterClientRequest(context, organizationName)
                    .setAppName("Leon's Bogus App")
                    .setAppVers(appVersion)
                    .setCellId(getCellId(context, me))
                    .build();
            if (useHostOverride) {
                reply = me.registerClient(request, hostOverride, portOverride, GRPC_TIMEOUT_MS);
            } else {
                reply = me.registerClient(request, me.generateDmeHostAddress(), me.getPort(), GRPC_TIMEOUT_MS);
            }
        } catch (PackageManager.NameNotFoundException nnfe) {
            Log.e(TAG, Log.getStackTraceString(nnfe));
            assertFalse("ExecutionException registering using PackageManager.", true);
        } catch (DmeDnsException dde) {
            Log.e(TAG, Log.getStackTraceString(dde));
            assertFalse("registerClientTest: DmeDnsException!", true);
        } catch (ExecutionException ee) {
            Log.e(TAG, Log.getStackTraceString(ee));
            assertFalse("registerClientTest: ExecutionException!", true);
        } catch (StatusRuntimeException sre) {
            Log.e(TAG, Log.getStackTraceString(sre));
            // This is expected when appName is wrong.
            assertEquals("NOT_FOUND: app not found", sre.getLocalizedMessage());
        } catch (InterruptedException ie) {
            Log.e(TAG, Log.getStackTraceString(ie));
            assertFalse("registerClientTest: InterruptedException!", true);
        }
    }

    @Test
    public void registerClientBadAppVersion() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MatchingEngine me = new MatchingEngine(context);
        me.setUseWifiOnly(useWifiOnly);
        me.setMatchingEngineLocationAllowed(true);
        me.setAllowSwitchIfNoSubscriberInfo(true);

        AppClient.RegisterClientReply reply = null;

        try {
            AppClient.RegisterClientRequest request = me.createDefaultRegisterClientRequest(context, organizationName)
                    .setAppName(applicationName)
                    .setAppVers("-999")
                    .setCellId(getCellId(context, me))
                    .build();
            if (useHostOverride) {
                reply = me.registerClient(request, hostOverride, portOverride, GRPC_TIMEOUT_MS);
            } else {
                reply = me.registerClient(request, me.generateDmeHostAddress(), me.getPort(), GRPC_TIMEOUT_MS);
            }
        } catch (PackageManager.NameNotFoundException nnfe) {
            Log.e(TAG, Log.getStackTraceString(nnfe));
            assertFalse("ExecutionException registering using PackageManager.", true);
        } catch (DmeDnsException dde) {
            Log.e(TAG, Log.getStackTraceString(dde));
            assertFalse("registerClientTest: DmeDnsException!", true);
        } catch (ExecutionException ee) {
            Log.e(TAG, Log.getStackTraceString(ee));
            assertFalse("registerClientTest: ExecutionException!", true);
        } catch (StatusRuntimeException sre) {
            Log.e(TAG, Log.getStackTraceString(sre));
            // This is expected when appVersion is wrong.
            assertEquals("NOT_FOUND: app not found", sre.getLocalizedMessage());
        } catch (InterruptedException ie) {
            Log.e(TAG, Log.getStackTraceString(ie));
            assertFalse("registerClientTest: InterruptedException!", true);
        }
    }

    @Test
    public void registerClientFutureTest() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MatchingEngine me = new MatchingEngine(context);
        me.setUseWifiOnly(useWifiOnly);
        me.setMatchingEngineLocationAllowed(true);
        me.setAllowSwitchIfNoSubscriberInfo(true);

        Future<AppClient.RegisterClientReply> registerReplyFuture;
        AppClient.RegisterClientReply reply = null;

        try {
            Location location = getTestLocation( 47.6062,122.3321);

            AppClient.RegisterClientRequest request = me.createDefaultRegisterClientRequest(context, organizationName)
                    .setAppName(applicationName)
                    .setAppVers(appVersion)
                    .setCellId(getCellId(context, me))
                    .setUniqueIdType("applicationInstallId")
                    .setUniqueId(me.getUniqueId(context))
                    .build();
            if (useHostOverride) {
                registerReplyFuture = me.registerClientFuture(request, hostOverride, portOverride, GRPC_TIMEOUT_MS);
            } else {
                registerReplyFuture = me.registerClientFuture(request, GRPC_TIMEOUT_MS);
            }
            reply = registerReplyFuture.get();
            assert(reply != null);
        } catch (PackageManager.NameNotFoundException nnfe) {
            Log.e(TAG, Log.getStackTraceString(nnfe));
            assertFalse("ExecutionException registering using PackageManager.", true);
        } catch (DmeDnsException dde) {
            Log.e(TAG, Log.getStackTraceString(dde));
            assertFalse("ExecutionException registering client.", true);
        } catch (ExecutionException ee) {
            Log.e(TAG, Log.getStackTraceString(ee));
            assertFalse("registerClientFutureTest: ExecutionException!", true);
        } catch (InterruptedException ie) {
            Log.e(TAG, Log.getStackTraceString(ie));
            assertFalse("registerClientFutureTest: InterruptedException!", true);
        }

        // TODO: Validate JWT
        Log.i(TAG, "registerClientFutureTest() response: " + reply.toString());
        assertEquals(0, reply.getVer());
        assertEquals(AppClient.ReplyStatus.RS_SUCCESS, reply.getStatus());
    }

}

