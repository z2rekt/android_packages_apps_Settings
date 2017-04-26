/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.settings.fuelgauge;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.Process;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.os.BatterySipper;
import com.android.internal.os.BatteryStatsHelper;
import com.android.internal.os.BatteryStatsImpl;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.SettingsRobolectricTestRunner;
import com.android.settings.TestConfig;
import com.android.settings.Utils;
import com.android.settings.applications.LayoutPreference;
import com.android.settings.testutils.FakeFeatureFactory;
import com.android.settingslib.BatteryInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static com.android.settings.fuelgauge.PowerUsageSummary.MENU_ADDITIONAL_BATTERY_INFO;
import static com.android.settings.fuelgauge.PowerUsageSummary.MENU_HIGH_POWER_APPS;
import static com.android.settings.fuelgauge.PowerUsageSummary.MENU_TOGGLE_APPS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PowerUsageSummary}.
 */
// TODO: Improve this test class so that it starts up the real activity and fragment.
@RunWith(SettingsRobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public class PowerUsageSummaryTest {
    private static final String[] PACKAGE_NAMES = {"com.app1", "com.app2"};
    private static final String TIME_LEFT = "2h30min";
    private static final int BATTERY_LEVEL = 55;
    private static final int UID = 123;
    private static final int POWER_MAH = 100;
    private static final long REMAINING_TIME_US = 100000;
    private static final long TIME_SINCE_LAST_FULL_CHARGE_MS = 25000;
    private static final long TIME_SINCE_LAST_FULL_CHARGE_US =
            TIME_SINCE_LAST_FULL_CHARGE_MS * 1000;
    private static final int DISCHARGE_AMOUNT = 100;
    private static final long USAGE_TIME_MS = 10000;
    private static final double TOTAL_POWER = 200;
    private static final double BATTERY_SCREEN_USAGE = 300;
    private static final double BATTERY_SYSTEM_USAGE = 600;
    private static final double BATTERY_OVERCOUNTED_USAGE = 500;
    private static final double PRECISION = 0.001;
    private static final double POWER_USAGE_PERCENTAGE = 50;
    private static final Intent ADDITIONAL_BATTERY_INFO_INTENT =
            new Intent("com.example.app.ADDITIONAL_BATTERY_INFO");

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Context mContext;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Menu mMenu;
    @Mock
    private MenuItem mAdditionalBatteryInfoMenu;
    @Mock
    private MenuItem mToggleAppsMenu;
    @Mock
    private MenuItem mHighPowerMenu;
    @Mock
    private MenuInflater mMenuInflater;
    @Mock
    private BatterySipper mNormalBatterySipper;
    @Mock
    private BatterySipper mScreenBatterySipper;
    @Mock
    private BatterySipper mOvercountedBatterySipper;
    @Mock
    private BatterySipper mSystemBatterySipper;
    @Mock
    private BatterySipper mCellBatterySipper;
    @Mock
    private PowerGaugePreference mPreference;
    @Mock
    private LayoutPreference mBatteryLayoutPref;
    @Mock
    private BatteryMeterView mBatteryMeterView;
    @Mock
    private TextView mBatteryPercentText;
    @Mock
    private TextView mSummary1;
    @Mock
    private BatteryInfo mBatteryInfo;
    @Mock
    private PowerGaugePreference mScreenUsagePref;
    @Mock
    private PowerGaugePreference mLastFullChargePref;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private BatteryStatsHelper mBatteryHelper;
    @Mock
    private PowerManager mPowerManager;
    @Mock
    private SettingsActivity mSettingsActivity;

    private List<BatterySipper> mUsageList;
    private Context mRealContext;
    private TestFragment mFragment;
    private FakeFeatureFactory mFeatureFactory;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mRealContext = RuntimeEnvironment.application;
        FakeFeatureFactory.setupForTest(mContext);
        mFeatureFactory = (FakeFeatureFactory) FakeFeatureFactory.getFactory(mContext);
        when(mContext.getSystemService(Context.POWER_SERVICE)).thenReturn(mPowerManager);

        mFragment = spy(new TestFragment(mContext));
        mFragment.initFeatureProvider();

        when(mFragment.getActivity()).thenReturn(mSettingsActivity);
        when(mAdditionalBatteryInfoMenu.getItemId())
                .thenReturn(MENU_ADDITIONAL_BATTERY_INFO);
        when(mToggleAppsMenu.getItemId()).thenReturn(MENU_TOGGLE_APPS);
        when(mHighPowerMenu.getItemId()).thenReturn(MENU_HIGH_POWER_APPS);
        when(mFeatureFactory.powerUsageFeatureProvider.getAdditionalBatteryInfoIntent())
                .thenReturn(ADDITIONAL_BATTERY_INFO_INTENT);
        when(mBatteryHelper.getTotalPower()).thenReturn(TOTAL_POWER);
        when(mBatteryHelper.getStats().computeBatteryRealtime(anyLong(), anyInt())).thenReturn(
                TIME_SINCE_LAST_FULL_CHARGE_US);

        when(mNormalBatterySipper.getPackages()).thenReturn(PACKAGE_NAMES);
        when(mNormalBatterySipper.getUid()).thenReturn(UID);
        mNormalBatterySipper.totalPowerMah = POWER_MAH;
        mNormalBatterySipper.drainType = BatterySipper.DrainType.APP;

        mCellBatterySipper.drainType = BatterySipper.DrainType.CELL;
        mCellBatterySipper.totalPowerMah = POWER_MAH;

        when(mBatteryLayoutPref.findViewById(R.id.summary1)).thenReturn(mSummary1);
        when(mBatteryLayoutPref.findViewById(R.id.battery_percent)).thenReturn(mBatteryPercentText);
        when(mBatteryLayoutPref.findViewById(R.id.battery_header_icon))
                .thenReturn(mBatteryMeterView);
        mFragment.setBatteryLayoutPreference(mBatteryLayoutPref);

        mScreenBatterySipper.drainType = BatterySipper.DrainType.SCREEN;
        mScreenBatterySipper.totalPowerMah = BATTERY_SCREEN_USAGE;
        mScreenBatterySipper.usageTimeMs = USAGE_TIME_MS;

        mSystemBatterySipper.drainType = BatterySipper.DrainType.APP;
        mSystemBatterySipper.totalPowerMah = BATTERY_SYSTEM_USAGE;
        when(mSystemBatterySipper.getUid()).thenReturn(Process.SYSTEM_UID);

        mOvercountedBatterySipper.drainType = BatterySipper.DrainType.OVERCOUNTED;
        mOvercountedBatterySipper.totalPowerMah = BATTERY_OVERCOUNTED_USAGE;

        mUsageList = new ArrayList<>();
        mUsageList.add(mNormalBatterySipper);
        mUsageList.add(mScreenBatterySipper);
        mUsageList.add(mCellBatterySipper);

        mFragment.mStatsHelper = mBatteryHelper;
        when(mBatteryHelper.getUsageList()).thenReturn(mUsageList);
        mFragment.mScreenUsagePref = mScreenUsagePref;
        mFragment.mLastFullChargePref = mLastFullChargePref;

        mBatteryInfo.mBatteryLevel = BATTERY_LEVEL;
    }

    @Test
    public void testOptionsMenu_additionalBatteryInfoEnabled() {
        when(mFeatureFactory.powerUsageFeatureProvider.isAdditionalBatteryInfoEnabled())
                .thenReturn(true);

        mFragment.onCreateOptionsMenu(mMenu, mMenuInflater);

        verify(mMenu).add(Menu.NONE, MENU_ADDITIONAL_BATTERY_INFO,
                Menu.NONE, R.string.additional_battery_info);

        mFragment.onOptionsItemSelected(mAdditionalBatteryInfoMenu);

        assertThat(mFragment.mStartActivityCalled).isTrue();
        assertThat(mFragment.mStartActivityIntent).isEqualTo(ADDITIONAL_BATTERY_INFO_INTENT);
    }

    @Test
    public void testOptionsMenu_additionalBatteryInfoDisabled() {
        when(mFeatureFactory.powerUsageFeatureProvider.isAdditionalBatteryInfoEnabled())
                .thenReturn(false);

        mFragment.onCreateOptionsMenu(mMenu, mMenuInflater);

        verify(mMenu, never()).add(Menu.NONE, MENU_ADDITIONAL_BATTERY_INFO,
                Menu.NONE, R.string.additional_battery_info);
    }

    @Test
    public void testOptionsMenu_menuHighPower_metricEventInvoked() {
        mFragment.onOptionsItemSelected(mHighPowerMenu);

        verify(mFeatureFactory.metricsFeatureProvider).action(mContext,
                MetricsProto.MetricsEvent.ACTION_SETTINGS_MENU_BATTERY_OPTIMIZATION);
    }

    @Test
    public void testOptionsMenu_menuAdditionalBattery_metricEventInvoked() {
        mFragment.onOptionsItemSelected(mAdditionalBatteryInfoMenu);

        verify(mFeatureFactory.metricsFeatureProvider).action(mContext,
                MetricsProto.MetricsEvent.ACTION_SETTINGS_MENU_BATTERY_USAGE_ALERTS);
    }

    @Test
    public void testOptionsMenu_menuAppToggle_metricEventInvoked() {
        mFragment.onOptionsItemSelected(mToggleAppsMenu);
        mFragment.mShowAllApps = false;

        verify(mFeatureFactory.metricsFeatureProvider).action(mContext,
                MetricsProto.MetricsEvent.ACTION_SETTINGS_MENU_BATTERY_APPS_TOGGLE, true);
    }

    @Test
    public void testOptionsMenu_toggleAppsEnabled() {
        when(mFeatureFactory.powerUsageFeatureProvider.isPowerAccountingToggleEnabled())
                .thenReturn(true);
        mFragment.mShowAllApps = false;

        mFragment.onCreateOptionsMenu(mMenu, mMenuInflater);

        verify(mMenu).add(Menu.NONE, MENU_TOGGLE_APPS, Menu.NONE, R.string.show_all_apps);
    }

    @Test
    public void testOptionsMenu_clickToggleAppsMenu_dataChanged() {
        testToggleAllApps(true);
        testToggleAllApps(false);
    }

    @Test
    public void testExtractKeyFromSipper_typeAPPUidObjectNull_returnPackageNames() {
        mNormalBatterySipper.uidObj = null;
        mNormalBatterySipper.drainType = BatterySipper.DrainType.APP;

        final String key = mFragment.extractKeyFromSipper(mNormalBatterySipper);
        assertThat(key).isEqualTo(TextUtils.concat(mNormalBatterySipper.getPackages()).toString());
    }

    @Test
    public void testExtractKeyFromSipper_typeOther_returnDrainType() {
        mNormalBatterySipper.uidObj = null;
        mNormalBatterySipper.drainType = BatterySipper.DrainType.BLUETOOTH;

        final String key = mFragment.extractKeyFromSipper(mNormalBatterySipper);
        assertThat(key).isEqualTo(mNormalBatterySipper.drainType.toString());
    }

    @Test
    public void testExtractKeyFromSipper_typeAPPUidObjectNotNull_returnUid() {
        mNormalBatterySipper.uidObj = new BatteryStatsImpl.Uid(new BatteryStatsImpl(), UID);
        mNormalBatterySipper.drainType = BatterySipper.DrainType.APP;

        final String key = mFragment.extractKeyFromSipper(mNormalBatterySipper);
        assertThat(key).isEqualTo(Integer.toString(mNormalBatterySipper.getUid()));
    }

    @Test
    public void testRemoveHiddenBatterySippers_containsHiddenSippers_removeAndReturnValue() {
        final List<BatterySipper> sippers = new ArrayList<>();
        sippers.add(mNormalBatterySipper);
        sippers.add(mScreenBatterySipper);
        sippers.add(mSystemBatterySipper);
        sippers.add(mOvercountedBatterySipper);
        when(mFeatureFactory.powerUsageFeatureProvider.isTypeSystem(mSystemBatterySipper))
                .thenReturn(true);

        final double totalUsage = mFragment.removeHiddenBatterySippers(sippers);
        assertThat(sippers).containsExactly(mNormalBatterySipper);
        assertThat(totalUsage).isWithin(PRECISION).of(BATTERY_SCREEN_USAGE + BATTERY_SYSTEM_USAGE);
    }

    @Test
    public void testShouldHideSipper_typeIdle_returnTrue() {
        mNormalBatterySipper.drainType = BatterySipper.DrainType.IDLE;
        assertThat(mFragment.shouldHideSipper(mNormalBatterySipper)).isTrue();
    }

    @Test
    public void testShouldHideSipper_TypeUnAccounted_ReturnTrue() {
        mNormalBatterySipper.drainType = BatterySipper.DrainType.UNACCOUNTED;
        assertThat(mFragment.shouldHideSipper(mNormalBatterySipper)).isTrue();
    }

    @Test
    public void testShouldHideSipper_TypeOverCounted_ReturnTrue() {
        mNormalBatterySipper.drainType = BatterySipper.DrainType.OVERCOUNTED;
        assertThat(mFragment.shouldHideSipper(mNormalBatterySipper)).isTrue();
    }

    @Test
    public void testShouldHideSipper_typeWifi_returnTrue() {
        mNormalBatterySipper.drainType = BatterySipper.DrainType.WIFI;
        assertThat(mFragment.shouldHideSipper(mNormalBatterySipper)).isTrue();
    }

    @Test
    public void testShouldHideSipper_typeCell_returnTrue() {
        mNormalBatterySipper.drainType = BatterySipper.DrainType.CELL;
        assertThat(mFragment.shouldHideSipper(mNormalBatterySipper)).isTrue();
    }

    @Test
    public void testShouldHideSipper_typeScreen_returnTrue() {
        mNormalBatterySipper.drainType = BatterySipper.DrainType.SCREEN;
        assertThat(mFragment.shouldHideSipper(mNormalBatterySipper)).isTrue();
    }

    @Test
    public void testShouldHideSipper_typeBluetooth_returnTrue() {
        mNormalBatterySipper.drainType = BatterySipper.DrainType.BLUETOOTH;
        assertThat(mFragment.shouldHideSipper(mNormalBatterySipper)).isTrue();
    }

    @Test
    public void testShouldHideSipper_typeSystem_returnTrue() {
        mNormalBatterySipper.drainType = BatterySipper.DrainType.APP;
        when(mNormalBatterySipper.getUid()).thenReturn(Process.ROOT_UID);
        when(mFeatureFactory.powerUsageFeatureProvider.isTypeSystem(Matchers.<BatterySipper>any()))
                .thenReturn(true);
        assertThat(mFragment.shouldHideSipper(mNormalBatterySipper)).isTrue();
    }

    @Test
    public void testShouldHideSipper_uidNormal_returnFalse() {
        mNormalBatterySipper.drainType = BatterySipper.DrainType.APP;
        when(mNormalBatterySipper.getUid()).thenReturn(UID);
        assertThat(mFragment.shouldHideSipper(mNormalBatterySipper)).isFalse();
    }

    @Test
    public void testShouldHideSipper_typeService_returnTrue() {
        mNormalBatterySipper.drainType = BatterySipper.DrainType.APP;
        when(mNormalBatterySipper.getUid()).thenReturn(UID);
        when(mFeatureFactory.powerUsageFeatureProvider.isTypeService(Matchers.<BatterySipper>any()))
                .thenReturn(true);

        assertThat(mFragment.shouldHideSipper(mNormalBatterySipper)).isTrue();
    }

    @Test
    public void testSetUsageSummary_timeLessThanOneMinute_doNotSetSummary() {
        final long usageTimeMs = 59 * DateUtils.SECOND_IN_MILLIS;

        mFragment.setUsageSummary(mPreference, "", usageTimeMs);
        verify(mPreference, never()).setSummary(anyString());
    }

    @Test
    public void testSetUsageSummary_timeMoreThanOneMinute_setSummary() {
        final long usageTimeMs = 2 * DateUtils.MINUTE_IN_MILLIS;

        mFragment.setUsageSummary(mPreference, "", usageTimeMs);
        verify(mPreference).setSummary(anyString());
    }

    @Test
    public void testUpdatePreference_hasRemainingTime_showRemainingLabel() {
        mBatteryInfo.remainingLabel = TIME_LEFT;

        mFragment.updateHeaderPreference(mBatteryInfo);

        verify(mSummary1).setText(mBatteryInfo.remainingLabel);
    }

    @Test
    public void testUpdatePreference_noRemainingTime_showStatusLabel() {
        mBatteryInfo.remainingLabel = null;

        mFragment.updateHeaderPreference(mBatteryInfo);

        verify(mSummary1).setText(mBatteryInfo.statusLabel);
    }

    @Test
    public void testUpdateHeaderPreference_asyncUpdate_shouldNotCrash() {
        when(mFragment.getContext()).thenReturn(null);
        mBatteryInfo.remainingTimeUs = REMAINING_TIME_US;

        //Should not crash
        mFragment.updateHeaderPreference(mBatteryInfo);
    }

    private void testToggleAllApps(final boolean isShowApps) {
        mFragment.mShowAllApps = isShowApps;

        mFragment.onOptionsItemSelected(mToggleAppsMenu);
        assertThat(mFragment.mShowAllApps).isEqualTo(!isShowApps);
    }

    @Test
    public void testFindBatterySipperByType_findTypeScreen() {
        BatterySipper sipper = mFragment.findBatterySipperByType(mUsageList,
                BatterySipper.DrainType.SCREEN);

        assertThat(sipper).isSameAs(mScreenBatterySipper);
    }

    @Test
    public void testFindBatterySipperByType_findTypeApp() {
        BatterySipper sipper = mFragment.findBatterySipperByType(mUsageList,
                BatterySipper.DrainType.APP);

        assertThat(sipper).isSameAs(mNormalBatterySipper);
    }

    @Test
    public void testUpdateScreenPreference_showCorrectSummary() {
        final String expectedUsedTime = Utils.formatElapsedTime(mRealContext, USAGE_TIME_MS, false);
        doReturn(mScreenBatterySipper).when(mFragment).findBatterySipperByType(any(), any());
        doReturn(mRealContext).when(mFragment).getContext();

        mFragment.updateScreenPreference();

        verify(mScreenUsagePref).setSubtitle(expectedUsedTime);
    }

    @Test
    public void testUpdateLastFullChargePreference_showCorrectSummary() {
        doReturn(mRealContext).when(mFragment).getContext();
        final String expected = mRealContext.getString(R.string.power_last_full_charge_summary,
                Utils.formatElapsedTime(mRealContext, TIME_SINCE_LAST_FULL_CHARGE_MS, false));
        doReturn(expected).when(mFragment).getString(eq(R.string.power_last_full_charge_summary),
                any());

        mFragment.updateLastFullChargePreference(TIME_SINCE_LAST_FULL_CHARGE_MS);

        verify(mLastFullChargePref).setSubtitle(expected);
    }

    @Test
    public void testUpdatePreference_usageListEmpty_shouldNotCrash() {
        when(mBatteryHelper.getUsageList()).thenReturn(new ArrayList<BatterySipper>());
        doReturn("").when(mFragment).getString(anyInt(), any());

        // Should not crash when update
        mFragment.updateScreenPreference();
    }

    @Test
    public void testCalculatePercentage() {
        final double percent = mFragment.calculatePercentage(POWER_MAH, DISCHARGE_AMOUNT);
        assertThat(percent).isWithin(PRECISION).of(POWER_USAGE_PERCENTAGE);
    }

    @Test
    public void testCalculateRunningTimeBasedOnStatsType() {
        assertThat(mFragment.calculateRunningTimeBasedOnStatsType()).isEqualTo(
                TIME_SINCE_LAST_FULL_CHARGE_MS);
    }

    public static class TestFragment extends PowerUsageSummary {

        private Context mContext;
        private boolean mStartActivityCalled;
        private Intent mStartActivityIntent;

        public TestFragment(Context context) {
            mContext = context;
        }

        @Override
        public Context getContext() {
            return mContext;
        }

        @Override
        public void startActivity(Intent intent) {
            mStartActivityCalled = true;
            mStartActivityIntent = intent;
        }

        @Override
        protected void refreshStats() {
            // Leave it empty for toggle apps menu test
        }
    }
}