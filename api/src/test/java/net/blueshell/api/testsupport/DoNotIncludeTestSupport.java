package net.blueshell.api.testsupport;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

public class DoNotIncludeTestSupport implements ImportOption {
    @Override
    public boolean includes(Location location) {
        return !location.contains("testsupport");
    }
}
