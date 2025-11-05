package net.blueshell.api.testsupport;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

/**
 * ArchUnit import option excluding factories from the application scan.
 */
public class DoNotIncludeFactory implements ImportOption {
    @Override
    public boolean includes(Location location) {
        return !location.contains("factory");
    }
}
