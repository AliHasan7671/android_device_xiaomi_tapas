/*
 * Copyright (C) 2023 Paranoid Android
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

#include <cstdlib>
#include <fstream>
#include <string.h>
#include <unistd.h>
#include <vector>

#include <android-base/properties.h>
#define _REALLY_INCLUDE_SYS__SYSTEM_PROPERTIES_H_
#include <sys/_system_properties.h>
#include <sys/sysinfo.h>

#include "property_service.h"
#include "vendor_init.h"

using android::base::GetProperty;
using std::string;

std::vector<std::string> ro_props_default_source_order = {
    "",
    "odm.",
    "product.",
    "system.",
    "system_dlkm.",
    "system_ext.",
    "vendor.",
    "vendor_dlkm.",
};

void property_override(string prop, string value, bool add = true) {
    auto pi = (prop_info*) __system_property_find(prop.c_str());

    if (pi != nullptr)
        __system_property_update(pi, value.c_str(), value.size());
    else if (add)
        __system_property_add(prop.c_str(), prop.size(), value.c_str(), value.size());
}

void set_ro_build_prop(const std::string &prop, const std::string &value) {
    for (const auto &source : ro_props_default_source_order) {
        auto prop_name = "ro." + source + "build." + prop;
        if (source == "")
            property_override(prop_name.c_str(), value.c_str());
        else
            property_override(prop_name.c_str(), value.c_str(), false);
    }
}

void load_redmi_properties(string hwname) {
    string device_name;
    string model;
    string mod_device;
    string fingerprint;
    string description;

    if (hwname == "tapas") {
	    device_name = "tapas";
        model = "23021RAAEG";
        mod_device = "tapas_global";
        fingerprint = "Redmi/tapas_global/tapas:13/TKQ1.221114.001/V816.0.4.0.UMTMIXM:user/release-keys";
        description = "tapas_global-user 13 TKQ1.221114.001 V816.0.4.0.UMTMIXM release-keys";
    } else if (hwname == "topaz") {
        device_name = "topaz";
        model = "23021RAA2Y";
        mod_device = "topaz_global";
        fingerprint = "Redmi/topaz_global/topaz:13/TKQ1.221114.001/V816.0.4.0.UMGMIXM:user/release-keys";
        description = "tapas_global-user 13 TKQ1.221114.001 V816.0.4.0.UMGMIXM release-keys";
    }

    // Set additional properties
    set_ro_build_prop("fingerprint", fingerprint);
    set_ro_build_prop("description", description);

    property_override("bluetooth.device.default_name", "Redmi Note 12");
    property_override("ro.product.brand", "Redmi");
    property_override("ro.product.device", device_name);
    property_override("ro.product.manufacturer", "Xiaomi");
    property_override("ro.product.marketname", "Redmi Note 12");
    property_override("ro.product.model", model);
    property_override("ro.product.mod_device", mod_device);
    property_override("ro.product.name", mod_device);
    property_override("vendor.usb.product_string", "Redmi Note 12");
}

void vendor_load_properties() {
    std::string hwname = GetProperty("ro.boot.hwname", "");
    if (access("/system/bin/recovery", F_OK) != 0) {
        load_redmi_properties(hwname);
    }

    // Set hardware revision
    property_override("ro.boot.hardware.revision", GetProperty("ro.boot.hwversion", "").c_str());

    // Set dalvik heap configuration
    std::string heapstartsize, heapgrowthlimit, heapsize, heapminfree,
        heapmaxfree, heaptargetutilization;

    struct sysinfo sys;
    sysinfo(&sys);

    if (sys.totalram > 5072ull * 1024 * 1024) {
        heapstartsize = "16m";
        heapgrowthlimit = "256m";
        heapsize = "512m";
        heaptargetutilization = "0.5";
        heapminfree = "8m";
        heapmaxfree = "32m";
    } else if (sys.totalram > 3072ull * 1024 * 1024) {
        heapstartsize = "8m";
        heapgrowthlimit = "192m";
        heapsize = "512m";
        heaptargetutilization = "0.6";
        heapminfree = "8m";
        heapmaxfree = "16m";
    } else {
        heapstartsize = "16m";
        heapgrowthlimit = "256m";
        heapsize = "512m";
        heaptargetutilization = "0.5";
        heapminfree = "8m";
        heapmaxfree = "32m";
    }

    property_override("dalvik.vm.heapstartsize", heapstartsize);
    property_override("dalvik.vm.heapgrowthlimit", heapgrowthlimit);
    property_override("dalvik.vm.heapsize", heapsize);
    property_override("dalvik.vm.heaptargetutilization", heaptargetutilization);
    property_override("dalvik.vm.heapminfree", heapminfree);
    property_override("dalvik.vm.heapmaxfree", heapmaxfree);
}

