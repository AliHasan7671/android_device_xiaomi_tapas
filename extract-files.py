#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: 2024 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

from extract_utils.file import File
from extract_utils.fixups_blob import (
    BlobFixupCtx,
    blob_fixup,
    blob_fixups_user_type,
)

from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)

def blob_fixup_test_flag(
    ctx: BlobFixupCtx,
    file: File,
    file_path: str,
    *args,
    **kargs,
):
    with open(file_path, 'rb+') as f:
        f.seek(1337)
        f.write(b'\x01')


blob_fixups: blob_fixups_user_type = {
    'vendor/bin/STFlashTool': blob_fixup()
        .add_needed('libbase_shim.so'),

    'vendor/etc/libnfc-nci.conf': blob_fixup()
       .regex_replace('/data/nfc', '/data/vendor/nfc'),

    'vendor/etc/init/android.hardware.gnss-aidl-service-qti.rc': blob_fixup()
       .regex_replace(' vendor_ssgtzd', ''),

    'vendor/lib64/hw/audio.primary.bengal.so': blob_fixup()
        .add_needed('libstagefright_foundation-v33.so'),

    'vendor/bin/hw/android.hardware.security.keymint-service-qti': blob_fixup()
        .add_needed('android.hardware.security.rkp-V3-ndk.so'),

    ('vendor/lib64/libqcrilNr.so', 'vendor/lib64/libril-db.so', 'vendor/etc/qcril_database/upgrade/config/6.0_config.sql'): blob_fixup()
        .binary_regex_replace(rb'persist\.vendor\.radio\.poweron_opt', rb'persist.vendor.radio.poweron_ign'),

    'vendor/lib64/vendor.libdpmframework.so': blob_fixup()
        .add_needed('libhidlbase_shim.so'),

    'vendor/etc/seccomp_policy/c2audio.vendor.ext-arm64.policy': blob_fixup()
        .add_line_if_missing('setsockopt: 1'),
}

module = ExtractUtilsModule(
    'tapas',
    'xiaomi',
    blob_fixups=blob_fixups,
    check_elf=False,
)

if __name__ == '__main__':
    utils = ExtractUtils.device(module)
    utils.run()
