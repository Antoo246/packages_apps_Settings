/*
 * Copyright (C) 2017 The Android Open Source Project
 *
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
 */

package com.android.settings.applications;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.BatteryStats;
import android.os.UserManager;
import android.support.v7.preference.Preference;
import android.view.View;
import android.widget.Button;

import com.android.internal.os.BatterySipper;
import com.android.internal.os.BatteryStatsHelper;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.SettingsRobolectricTestRunner;
import com.android.settings.TestConfig;
import com.android.settings.applications.instantapps.InstantAppButtonsController;
import com.android.settings.testutils.FakeFeatureFactory;
import com.android.settingslib.applications.AppUtils;
import com.android.settingslib.applications.ApplicationsState.AppEntry;
import com.android.settingslib.applications.instantapps.InstantAppDataProvider;
import com.android.settingslib.applications.StorageStatsSource.AppStorageStats;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith(SettingsRobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public final class InstalledAppDetailsTest {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Context mContext;
    @Mock
    ApplicationFeatureProvider mApplicationFeatureProvider;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private UserManager mUserManager;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private SettingsActivity mActivity;
    @Mock
    private DevicePolicyManager mDevicePolicyManager;
    @Mock
    private Preference mBatteryPreference;
    @Mock
    private BatterySipper mBatterySipper;
    @Mock
    private BatteryStatsHelper mBatteryStatsHelper;
    @Mock
    private BatteryStats.Uid mUid;

    private InstalledAppDetails mAppDetail;
    private Context mShadowContext;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mShadowContext = RuntimeEnvironment.application;

        mAppDetail = spy(new InstalledAppDetails());

        mBatterySipper.drainType = BatterySipper.DrainType.IDLE;
        mBatterySipper.uidObj = mUid;
        doReturn(mActivity).when(mAppDetail).getActivity();
        doReturn(mShadowContext).when(mAppDetail).getContext();

        // Default to not considering any apps to be instant (individual tests can override this).
        ReflectionHelpers.setStaticField(AppUtils.class, "sInstantAppDataProvider",
                (InstantAppDataProvider) (i -> false));
    }

    @Test
    public void shouldShowUninstallForAll_installForOneOtherUserOnly_shouldReturnTrue() {
        when(mDevicePolicyManager.packageHasActiveAdmins(anyString())).thenReturn(false);
        when(mUserManager.getUsers().size()).thenReturn(2);
        ReflectionHelpers.setField(mAppDetail, "mDpm", mDevicePolicyManager);
        ReflectionHelpers.setField(mAppDetail, "mUserManager", mUserManager);
        final ApplicationInfo info = new ApplicationInfo();
        info.enabled = true;
        final AppEntry appEntry = mock(AppEntry.class);
        appEntry.info = info;
        final PackageInfo packageInfo = mock(PackageInfo.class);
        ReflectionHelpers.setField(mAppDetail, "mPackageInfo", packageInfo);

        assertThat(mAppDetail.shouldShowUninstallForAll(appEntry)).isTrue();
    }

    @Test
    public void shouldShowUninstallForAll_installForSelfOnly_shouldReturnFalse() {
        when(mDevicePolicyManager.packageHasActiveAdmins(anyString())).thenReturn(false);
        when(mUserManager.getUsers().size()).thenReturn(2);
        ReflectionHelpers.setField(mAppDetail, "mDpm", mDevicePolicyManager);
        ReflectionHelpers.setField(mAppDetail, "mUserManager", mUserManager);
        final ApplicationInfo info = new ApplicationInfo();
        info.flags = ApplicationInfo.FLAG_INSTALLED;
        info.enabled = true;
        final AppEntry appEntry = mock(AppEntry.class);
        appEntry.info = info;
        final PackageInfo packageInfo = mock(PackageInfo.class);
        ReflectionHelpers.setField(mAppDetail, "mPackageInfo", packageInfo);

        assertThat(mAppDetail.shouldShowUninstallForAll(appEntry)).isFalse();
    }

    @Test
    public void getStorageSummary_shouldWorkForExternal() {
        Context context = RuntimeEnvironment.application.getApplicationContext();
        AppStorageStats stats = mock(AppStorageStats.class);
        when(stats.getTotalBytes()).thenReturn(1L);

        assertThat(InstalledAppDetails.getStorageSummary(context, stats, true))
                .isEqualTo("1.00B used in external storage");
    }

    @Test
    public void getStorageSummary_shouldWorkForInternal() {
        Context context = RuntimeEnvironment.application.getApplicationContext();
        AppStorageStats stats = mock(AppStorageStats.class);
        when(stats.getTotalBytes()).thenReturn(1L);

        assertThat(InstalledAppDetails.getStorageSummary(context, stats, false))
                .isEqualTo("1.00B used in internal storage");
    }

    @Test
    public void launchFragment_hasNoPackageInfo_shouldFinish() {
        ReflectionHelpers.setField(mAppDetail, "mPackageInfo", null);

        assertThat(mAppDetail.ensurePackageInfoAvailable(mActivity)).isFalse();
        verify(mActivity).finishAndRemoveTask();
    }

    @Test
    public void launchFragment_hasPackageInfo_shouldReturnTrue() {
        final PackageInfo packageInfo = mock(PackageInfo.class);
        ReflectionHelpers.setField(mAppDetail, "mPackageInfo", packageInfo);

        assertThat(mAppDetail.ensurePackageInfoAvailable(mActivity)).isTrue();
        verify(mActivity, never()).finishAndRemoveTask();
    }

    @Test
    public void launchPowerUsageDetailFragment_shouldNotCrash() {
        mAppDetail.mBatteryPreference = mBatteryPreference;
        mAppDetail.mSipper = mBatterySipper;
        mAppDetail.mBatteryHelper = mBatteryStatsHelper;

        // Should not crash
        mAppDetail.onPreferenceClick(mBatteryPreference);
    }

    // Tests that we don't show the "uninstall for all users" button for instant apps.
    @Test
    public void instantApps_noUninstallForAllButton() {
        // Make this app appear to be instant.
        ReflectionHelpers.setStaticField(AppUtils.class, "sInstantAppDataProvider",
                (InstantAppDataProvider) (i -> true));
        when(mDevicePolicyManager.packageHasActiveAdmins(anyString())).thenReturn(false);
        when(mUserManager.getUsers().size()).thenReturn(2);

        final ApplicationInfo info = new ApplicationInfo();
        info.enabled = true;
        final AppEntry appEntry = mock(AppEntry.class);
        appEntry.info = info;
        final PackageInfo packageInfo = mock(PackageInfo.class);

        ReflectionHelpers.setField(mAppDetail, "mDpm", mDevicePolicyManager);
        ReflectionHelpers.setField(mAppDetail, "mUserManager", mUserManager);
        ReflectionHelpers.setField(mAppDetail, "mPackageInfo", packageInfo);

        assertThat(mAppDetail.shouldShowUninstallForAll(appEntry)).isFalse();
    }

    // Tests that we don't show the uninstall button for instant apps"
    @Test
    public void instantApps_noUninstallButton() {
        // Make this app appear to be instant.
        ReflectionHelpers.setStaticField(AppUtils.class, "sInstantAppDataProvider",
                (InstantAppDataProvider) (i -> true));
        final ApplicationInfo info = new ApplicationInfo();
        info.flags = ApplicationInfo.FLAG_INSTALLED;
        info.enabled = true;
        final AppEntry appEntry = mock(AppEntry.class);
        appEntry.info = info;
        final PackageInfo packageInfo = mock(PackageInfo.class);
        packageInfo.applicationInfo = info;
        final Button uninstallButton = mock(Button.class);

        ReflectionHelpers.setField(mAppDetail, "mUserManager", mUserManager);
        ReflectionHelpers.setField(mAppDetail, "mAppEntry", appEntry);
        ReflectionHelpers.setField(mAppDetail, "mPackageInfo", packageInfo);
        ReflectionHelpers.setField(mAppDetail, "mUninstallButton", uninstallButton);

        mAppDetail.initUnintsallButtonForUserApp();
        verify(uninstallButton).setVisibility(View.GONE);
    }

    // Tests that we don't show the force stop button for instant apps (they aren't allowed to run
    // when they aren't in the foreground).
    @Test
    public void instantApps_noForceStop() {
        // Make this app appear to be instant.
        ReflectionHelpers.setStaticField(AppUtils.class, "sInstantAppDataProvider",
                (InstantAppDataProvider) (i -> true));
        final PackageInfo packageInfo = mock(PackageInfo.class);
        final AppEntry appEntry = mock(AppEntry.class);
        final ApplicationInfo info = new ApplicationInfo();
        appEntry.info = info;
        final Button forceStopButton = mock(Button.class);

        ReflectionHelpers.setField(mAppDetail, "mDpm", mDevicePolicyManager);
        ReflectionHelpers.setField(mAppDetail, "mPackageInfo", packageInfo);
        ReflectionHelpers.setField(mAppDetail, "mAppEntry", appEntry);
        ReflectionHelpers.setField(mAppDetail, "mForceStopButton", forceStopButton);

        mAppDetail.checkForceStop();
        verify(forceStopButton).setVisibility(View.GONE);
    }

    @Test
    public void instantApps_buttonControllerHandlesDialog() {
        InstantAppButtonsController mockController = mock(InstantAppButtonsController.class);
        ReflectionHelpers.setField(
                mAppDetail, "mInstantAppButtonsController", mockController);
        // Make sure first that button controller is not called for supported dialog id
        AlertDialog mockDialog = mock(AlertDialog.class);
        when(mockController.createDialog(InstantAppButtonsController.DLG_CLEAR_APP))
                .thenReturn(mockDialog);
        assertThat(mAppDetail.createDialog(InstantAppButtonsController.DLG_CLEAR_APP, 0))
                .isEqualTo(mockDialog);
        verify(mockController).createDialog(InstantAppButtonsController.DLG_CLEAR_APP);
    }

    // A helper class for testing the InstantAppButtonsController - it lets us look up the
    // preference associated with a key for instant app buttons and get back a mock
    // LayoutPreference (to avoid a null pointer exception).
    public static class InstalledAppDetailsWithMockInstantButtons extends InstalledAppDetails {
        @Mock
        private LayoutPreference mInstantButtons;

        public InstalledAppDetailsWithMockInstantButtons() {
            super();
            MockitoAnnotations.initMocks(this);
        }

        @Override
        public Preference findPreference(CharSequence key) {
            if (key == "instant_app_buttons") {
                return mInstantButtons;
            }
            return super.findPreference(key);
        }
    }

    @Test
    public void instantApps_instantSpecificButtons() {
        // Make this app appear to be instant.
        ReflectionHelpers.setStaticField(AppUtils.class, "sInstantAppDataProvider",
                (InstantAppDataProvider) (i -> true));
        final PackageInfo packageInfo = mock(PackageInfo.class);

        final InstalledAppDetailsWithMockInstantButtons
                fragment = new InstalledAppDetailsWithMockInstantButtons();
        ReflectionHelpers.setField(fragment, "mPackageInfo", packageInfo);

        final InstantAppButtonsController buttonsController =
                mock(InstantAppButtonsController.class);
        when(buttonsController.setPackageName(anyString())).thenReturn(buttonsController);

        FakeFeatureFactory.setupForTest(mContext);
        FakeFeatureFactory factory =
                (FakeFeatureFactory) FakeFeatureFactory.getFactory(mContext);
        when(factory.applicationFeatureProvider.newInstantAppButtonsController(
                any(), any(), any())).thenReturn(buttonsController);

        fragment.maybeAddInstantAppButtons();
        verify(buttonsController).setPackageName(anyString());
        verify(buttonsController).show();
    }
}
