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
package com.android.settings.fuelgauge;

import android.content.Context;
import android.content.pm.PackageManager;

import com.android.internal.os.BatterySipper;
import com.android.internal.os.BatterySipper.DrainType;
import com.android.internal.os.BatteryStatsHelper;
import com.android.settings.R;
import com.android.settings.SettingsRobolectricTestRunner;
import com.android.settings.TestConfig;
import com.android.settings.Utils;
import com.android.settings.fuelgauge.PowerUsageAdvanced.PowerUsageData;
import com.android.settings.fuelgauge.PowerUsageAdvanced.PowerUsageData.UsageType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SettingsRobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public class PowerUsageAdvancedTest {
    private static final int FAKE_UID_1 = 50;
    private static final int FAKE_UID_2 = 100;
    private static final int DISCHARGE_AMOUNT = 60;
    private static final double TYPE_APP_USAGE = 80;
    private static final double TYPE_BLUETOOTH_USAGE = 50;
    private static final double TYPE_WIFI_USAGE = 0;
    private static final double TOTAL_USAGE = TYPE_APP_USAGE * 2 + TYPE_BLUETOOTH_USAGE
            + TYPE_WIFI_USAGE;
    private static final double TOTAL_POWER = 500;
    private static final double PRECISION = 0.001;
    private static final String STUB_STRING = "stub_string";
    @Mock
    private BatterySipper mNormalBatterySipper;
    @Mock
    private BatterySipper mMaxBatterySipper;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private BatteryStatsHelper mBatteryStatsHelper;
    @Mock
    private PowerUsageFeatureProvider mPowerUsageFeatureProvider;
    @Mock
    private PackageManager mPackageManager;
    private PowerUsageAdvanced mPowerUsageAdvanced;
    private PowerUsageData mPowerUsageData;
    private Context mShadowContext;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mShadowContext = RuntimeEnvironment.application;
        mPowerUsageAdvanced = spy(new PowerUsageAdvanced());

        List<BatterySipper> batterySippers = new ArrayList<>();
        batterySippers.add(new BatterySipper(DrainType.APP,
                new FakeUid(FAKE_UID_1), TYPE_APP_USAGE));
        batterySippers.add(new BatterySipper(DrainType.APP,
                new FakeUid(FAKE_UID_2), TYPE_APP_USAGE));
        batterySippers.add(new BatterySipper(DrainType.BLUETOOTH, new FakeUid(FAKE_UID_1),
                TYPE_BLUETOOTH_USAGE));
        batterySippers.add(new BatterySipper(DrainType.WIFI, new FakeUid(FAKE_UID_1),
                TYPE_WIFI_USAGE));

        when(mBatteryStatsHelper.getStats().getDischargeAmount(anyInt())).thenReturn(
                DISCHARGE_AMOUNT);
        when(mBatteryStatsHelper.getUsageList()).thenReturn(batterySippers);
        when(mBatteryStatsHelper.getTotalPower()).thenReturn(TOTAL_USAGE);
        when(mPowerUsageAdvanced.getContext()).thenReturn(mShadowContext);
        doReturn(STUB_STRING).when(mPowerUsageAdvanced).getString(anyInt(), any(), any());
        doReturn(STUB_STRING).when(mPowerUsageAdvanced).getString(anyInt(), any());
        mPowerUsageAdvanced.setPackageManager(mPackageManager);
        mPowerUsageAdvanced.setPowerUsageFeatureProvider(mPowerUsageFeatureProvider);

        mPowerUsageData = new PowerUsageData(UsageType.APP);
        mMaxBatterySipper.totalPowerMah = TYPE_BLUETOOTH_USAGE;
        mMaxBatterySipper.drainType = DrainType.BLUETOOTH;
        mNormalBatterySipper.drainType = DrainType.SCREEN;
    }

    @Test
    public void testExtractUsageType_TypeSystem_ReturnSystem() {
        mNormalBatterySipper.drainType = DrainType.APP;
        when(mPowerUsageFeatureProvider.isTypeSystem(any())).thenReturn(true);

        assertThat(mPowerUsageAdvanced.extractUsageType(mNormalBatterySipper))
                .isEqualTo(UsageType.SYSTEM);
    }

    @Test
    public void testExtractUsageType_TypeEqualsToDrainType_ReturnRelevantType() {
        final DrainType drainTypes[] = {DrainType.WIFI, DrainType.BLUETOOTH, DrainType.IDLE,
                DrainType.USER, DrainType.CELL, DrainType.UNACCOUNTED};
        final int usageTypes[] = {UsageType.WIFI, UsageType.BLUETOOTH, UsageType.IDLE,
                UsageType.USER, UsageType.CELL, UsageType.UNACCOUNTED};

        assertThat(drainTypes.length).isEqualTo(usageTypes.length);
        for (int i = 0, size = drainTypes.length; i < size; i++) {
            mNormalBatterySipper.drainType = drainTypes[i];
            assertThat(mPowerUsageAdvanced.extractUsageType(mNormalBatterySipper))
                    .isEqualTo(usageTypes[i]);
        }
    }

    @Test
    public void testExtractUsageType_TypeService_ReturnService() {
        mNormalBatterySipper.drainType = DrainType.APP;
        when(mNormalBatterySipper.getUid()).thenReturn(FAKE_UID_1);
        when(mPowerUsageFeatureProvider.isTypeService(any())).thenReturn(true);

        assertThat(mPowerUsageAdvanced.extractUsageType(mNormalBatterySipper))
                .isEqualTo(UsageType.SERVICE);
    }

    @Test
    public void testParsePowerUsageData_PercentageCalculatedCorrectly() {
        final double percentApp = TYPE_APP_USAGE * 2 / TOTAL_USAGE * DISCHARGE_AMOUNT;
        final double percentWifi = TYPE_WIFI_USAGE / TOTAL_USAGE * DISCHARGE_AMOUNT;
        final double percentBluetooth = TYPE_BLUETOOTH_USAGE / TOTAL_USAGE * DISCHARGE_AMOUNT;

        List<PowerUsageData> batteryData =
                mPowerUsageAdvanced.parsePowerUsageData(mBatteryStatsHelper);
        for (PowerUsageData data : batteryData) {
            switch (data.usageType) {
                case UsageType.WIFI:
                    assertThat(data.percentage).isWithin(PRECISION).of(percentWifi);
                    break;
                case UsageType.APP:
                    assertThat(data.percentage).isWithin(PRECISION).of(percentApp);
                    break;
                case UsageType.BLUETOOTH:
                    assertThat(data.percentage).isWithin(PRECISION).of(percentBluetooth);
                    break;
                default:
                    break;
            }
        }
    }

    @Test
    public void testUpdateUsageDataSummary_onlyOneApp_showUsageTime() {
        mPowerUsageData.usageList.add(mNormalBatterySipper);
        mPowerUsageAdvanced.updateUsageDataSummary(mPowerUsageData, TOTAL_POWER, DISCHARGE_AMOUNT);

        verify(mPowerUsageAdvanced).getString(eq(R.string.battery_used_for), any());
    }

    @Test
    public void testUpdateUsageDataSummary_moreThanOneApp_showMaxUsageApp() {
        mPowerUsageData.usageList.add(mNormalBatterySipper);
        mPowerUsageData.usageList.add(mMaxBatterySipper);
        doReturn(mMaxBatterySipper).when(mPowerUsageAdvanced).findBatterySipperWithMaxBatteryUsage(
                mPowerUsageData.usageList);
        final double percentage = (TYPE_BLUETOOTH_USAGE / TOTAL_POWER) * DISCHARGE_AMOUNT;
        mPowerUsageAdvanced.updateUsageDataSummary(mPowerUsageData, TOTAL_POWER, DISCHARGE_AMOUNT);

        verify(mPowerUsageAdvanced).getString(eq(R.string.battery_used_by),
                eq(Utils.formatPercentage(percentage, true)), any());
    }

    @Test
    public void testFindBatterySipperWithMaxBatteryUsage_findCorrectOne() {
        mPowerUsageData.usageList.add(mNormalBatterySipper);
        mPowerUsageData.usageList.add(mMaxBatterySipper);
        BatterySipper sipper = mPowerUsageAdvanced.findBatterySipperWithMaxBatteryUsage(
                mPowerUsageData.usageList);

        assertThat(sipper).isEqualTo(mMaxBatterySipper);
    }

    @Test
    public void testInit_ContainsAllUsageType() {
        final int[] usageTypeSet = mPowerUsageAdvanced.mUsageTypes;

        assertThat(usageTypeSet).asList().containsExactly(UsageType.APP, UsageType.WIFI,
                UsageType.CELL, UsageType.BLUETOOTH, UsageType.IDLE, UsageType.SERVICE,
                UsageType.USER, UsageType.SYSTEM, UsageType.UNACCOUNTED, UsageType.OVERCOUNTED);
    }

    @Test
    public void testPowerUsageData_SortedByUsage() {
        List<PowerUsageData> dataList = new ArrayList<>();

        dataList.add(new PowerUsageData(UsageType.WIFI, TYPE_WIFI_USAGE));
        dataList.add(new PowerUsageData(UsageType.BLUETOOTH, TYPE_BLUETOOTH_USAGE));
        dataList.add(new PowerUsageData(UsageType.APP, TYPE_APP_USAGE));
        Collections.sort(dataList);

        for (int i = 1, size = dataList.size(); i < size; i++) {
            assertThat(dataList.get(i - 1).totalPowerMah).isAtLeast(dataList.get(i).totalPowerMah);
        }
    }

    @Test
    public void testShouldHide_typeUnAccounted_returnTrue() {
        mPowerUsageData.usageType = UsageType.UNACCOUNTED;

        assertThat(mPowerUsageAdvanced.shouldHide(mPowerUsageData)).isTrue();
    }


    @Test
    public void testShouldHide_typeOverCounted_returnTrue() {
        mPowerUsageData.usageType = UsageType.OVERCOUNTED;

        assertThat(mPowerUsageAdvanced.shouldHide(mPowerUsageData)).isTrue();
    }


    @Test
    public void testShouldHide_typeNormal_returnFalse() {
        mPowerUsageData.usageType = UsageType.APP;

        assertThat(mPowerUsageAdvanced.shouldHide(mPowerUsageData)).isFalse();
    }
}