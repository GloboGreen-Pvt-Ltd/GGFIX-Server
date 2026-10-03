package com.repairshop.saas.common.devicespecs;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pure category/attribute rules — no I/O, so it is unit-testable on its own.
 * {@link DeviceSpecService} supplies the master-data lookup.
 */
public final class DeviceSpecValidator {

    private DeviceSpecValidator() {}

    private static final String CATEGORY_VALUES = Arrays.stream(DeviceSpecCategory.values())
            .map(Enum::name).collect(Collectors.joining(", "));

    /**
     * Validate and normalize the device attributes of one create/update request.
     *
     * @param raw                 what the client sent
     * @param masterSupported     extra attributes master data explicitly enables for
     *                            the category / model — called only when the request
     *                            carries an attribute outside the category's base set
     * @param ramOptionIdSent     the request also carried ramOptionId
     * @param storageOptionIdSent the request also carried storageOptionId
     * @return the normalized specs, or {@code null} when the request carries no
     *         deviceCategory and no attributes — an older client that predates
     *         category specs; the caller leaves the stored columns as they are
     * @throws IllegalArgumentException (→ HTTP 400) on an unknown category, an
     *         attribute that doesn't belong to the category, or a malformed value
     */
    public static DeviceSpecs validate(DeviceSpecs raw,
                                       Function<DeviceSpecCategory, Set<DeviceSpecKey>> masterSupported,
                                       boolean ramOptionIdSent,
                                       boolean storageOptionIdSent) {
        DeviceSpecs in = raw == null ? DeviceSpecs.EMPTY : raw;
        if (DeviceSpecs.isBlank(in.deviceCategory())) {
            if (in.hasAnyAttribute()) {
                throw new IllegalArgumentException(
                        "deviceCategory is required when ram, storageCapacity, storageType, caseSize, connectivity or deviceType is sent");
            }
            return null;
        }
        DeviceSpecCategory category = DeviceSpecCategory.fromCode(in.deviceCategory())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown deviceCategory '" + in.deviceCategory() + "'. Expected one of " + CATEGORY_VALUES));

        if (!category.usesMasterOptionIds() && (ramOptionIdSent || storageOptionIdSent)) {
            throw new IllegalArgumentException(
                    "ramOptionId / storageOptionId apply only to MOBILE and TABLET. Send the "
                            + category.name() + " specifications as their own fields instead");
        }

        Set<DeviceSpecKey> allowed = category.baseKeys();
        Map<DeviceSpecKey, String> out = new EnumMap<>(DeviceSpecKey.class);
        boolean lookedUp = false;
        for (DeviceSpecKey key : DeviceSpecKey.values()) {
            String value = in.get(key);
            if (DeviceSpecs.isBlank(value)) continue;
            if (!allowed.contains(key) && !category.usesMasterOptionIds() && !lookedUp && masterSupported != null) {
                Set<DeviceSpecKey> extra = masterSupported.apply(category);
                if (extra != null) allowed.addAll(extra);
                lookedUp = true;
            }
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException(
                        key.jsonKey() + " is not applicable to " + category.name() + " devices");
            }
            out.put(key, DeviceSpecNormalizer.normalize(key, value));
        }

        return new DeviceSpecs(
                category.name(),
                out.get(DeviceSpecKey.RAM),
                out.get(DeviceSpecKey.STORAGE_CAPACITY),
                out.get(DeviceSpecKey.STORAGE_TYPE),
                out.get(DeviceSpecKey.CASE_SIZE),
                out.get(DeviceSpecKey.CONNECTIVITY),
                out.get(DeviceSpecKey.DEVICE_TYPE));
    }
}
