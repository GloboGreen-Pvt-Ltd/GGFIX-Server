package com.repairshop.saas.common.devicespecs;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeviceSpecValidatorTest {

    private static DeviceSpecs specs(String category, String ram, String storageCapacity, String storageType,
                                     String caseSize, String connectivity, String deviceType) {
        return new DeviceSpecs(category, ram, storageCapacity, storageType, caseSize, connectivity, deviceType);
    }

    private static DeviceSpecs validate(DeviceSpecs in) {
        return DeviceSpecValidator.validate(in, c -> EnumSet.noneOf(DeviceSpecKey.class), false, false);
    }

    @Test
    void laptopIsNormalizedToStoredForm() {
        DeviceSpecs out = validate(specs("LAPTOP", "16 GB", "1 tb", "NVMe SSD", null, null, null));
        assertEquals("LAPTOP", out.deviceCategory());
        assertEquals("16GB", out.ram());
        assertEquals("1TB", out.storageCapacity());
        assertEquals("NVME_SSD", out.storageType());
    }

    @Test
    void storageTypeAcceptsOnlyTheThreeKinds() {
        assertEquals("HDD", validate(specs("LAPTOP", null, null, "Hard Disk Drive (HDD)", null, null, null)).storageType());
        assertEquals("SATA_SSD", validate(specs("LAPTOP", null, null, "SATA SSD", null, null, null)).storageType());
        assertEquals("SATA_SSD", validate(specs("LAPTOP", null, null, "SSD", null, null, null)).storageType());
        assertEquals("NVME_SSD", validate(specs("LAPTOP", null, null, "NVME_SSD", null, null, null)).storageType());
        assertThrows(IllegalArgumentException.class,
                () -> validate(specs("LAPTOP", null, null, "NVMe SSD SSD", null, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> validate(specs("LAPTOP", null, null, "Optane", null, null, null)));
    }

    @Test
    void smartwatchAliasAndCodes() {
        DeviceSpecs out = validate(specs("SMARTWATCHES", null, null, null, "44 mm", "GPS + Cellular", null));
        assertEquals("SMARTWATCH", out.deviceCategory());
        assertEquals("44MM", out.caseSize());
        assertEquals("GPS_CELLULAR", out.connectivity());
        assertEquals("BLUETOOTH_WIFI",
                validate(specs("SMARTWATCH", null, null, null, null, "Bluetooth + Wi-Fi", null)).connectivity());
    }

    @Test
    void audioDeviceCodes() {
        DeviceSpecs out = validate(specs("AUDIO_DEVICE", null, null, null, null, "3.5 mm", "TWS Earbuds"));
        assertEquals("AUX_3_5MM", out.connectivity());
        assertEquals("TWS_EARBUDS", out.deviceType());
        assertEquals("USB_C", validate(specs("AUDIO_DEVICE", null, null, null, null, "USB-C", null)).connectivity());
    }

    @Test
    void smartwatchRejectsLaptopAttributes() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> validate(specs("SMARTWATCH", "16GB", null, "NVME_SSD", null, null, null)));
        assertTrue(e.getMessage().contains("not applicable to SMARTWATCH"), e.getMessage());
    }

    @Test
    void audioDeviceRejectsRamAndStorage() {
        assertThrows(IllegalArgumentException.class,
                () -> validate(specs("AUDIO_DEVICE", null, "512GB", null, null, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> validate(specs("AUDIO_DEVICE", null, null, null, "44MM", null, null)));
    }

    @Test
    void masterDataCanEnableAnOptionalAttribute() {
        DeviceSpecs out = DeviceSpecValidator.validate(
                specs("SMARTWATCH", null, "32 GB", null, null, null, null),
                c -> EnumSet.of(DeviceSpecKey.STORAGE_CAPACITY), false, false);
        assertEquals("32GB", out.storageCapacity());
    }

    @Test
    void masterLookupOnlyRunsForAttributesOutsideTheBaseSet() {
        int[] calls = {0};
        DeviceSpecValidator.validate(specs("LAPTOP", "8GB", "256GB", "HDD", null, null, null),
                c -> { calls[0]++; return Set.of(); }, false, false);
        assertEquals(0, calls[0]);
    }

    @Test
    void categoryWithNoAttributesIsValid() {
        // Booking → Your Device asks a Smartwatch / Audio Device for colour only.
        DeviceSpecs watch = validate(specs("SMARTWATCHES", null, null, null, null, null, null));
        assertEquals("SMARTWATCH", watch.deviceCategory());
        assertFalse(watch.hasAnyAttribute());
        assertEquals("AUDIO_DEVICE", validate(specs("AUDIO_DEVICE", null, null, null, null, null, null)).deviceCategory());
    }

    @Test
    void mobileAndTabletTakeNoTextAttributes() {
        assertNotNull(validate(specs("MOBILE", null, null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> validate(specs("MOBILE", "8GB", null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> validate(specs("TABLET", null, null, "SSD", null, null, null)));
    }

    @Test
    void optionIdsAreOnlyForMobileAndTablet() {
        assertNotNull(DeviceSpecValidator.validate(specs("MOBILE", null, null, null, null, null, null), null, true, true));
        assertThrows(IllegalArgumentException.class,
                () -> DeviceSpecValidator.validate(specs("LAPTOP", "8GB", null, null, null, null, null), null, true, false));
    }

    @Test
    void legacyRequestWithoutCategoryIsLeftAlone() {
        assertNull(validate(DeviceSpecs.EMPTY));
        assertNull(validate(null));
        assertThrows(IllegalArgumentException.class,
                () -> validate(specs(null, "8GB", null, null, null, null, null)));
    }

    @Test
    void unknownCategoryAndMalformedValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> validate(specs("TOASTER", null, null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> validate(specs("LAPTOP", "lots", null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> validate(specs("SMARTWATCH", null, null, null, "big", null, null)));
    }

    @Test
    void ramStorageMasterEntries() {
        assertEquals(EnumSet.of(DeviceSpecKey.RAM, DeviceSpecKey.STORAGE_CAPACITY),
                DeviceSpecService.keysFromRamStorage("[\"8 GB + 256 GB\"]"));
        assertEquals(EnumSet.of(DeviceSpecKey.STORAGE_CAPACITY),
                DeviceSpecService.keysFromRamStorage("[\"32 GB\"]"));
        assertEquals(EnumSet.noneOf(DeviceSpecKey.class),
                DeviceSpecService.keysFromRamStorage("[\"Black Silicone\",\"Blush Silicone\"]"));
        assertEquals(EnumSet.noneOf(DeviceSpecKey.class), DeviceSpecService.keysFromRamStorage("not json"));
    }
}
