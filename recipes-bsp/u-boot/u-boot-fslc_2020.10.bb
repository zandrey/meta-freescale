require recipes-bsp/u-boot/u-boot.inc
require u-boot-fslc-common_${PV}.inc

DESCRIPTION = "U-Boot based on mainline U-Boot used by FSL Community BSP in \
order to provide support for some backported features and fixes, or because it \
was submitted for revision and it takes some time to become part of a stable \
version, or because it is not applicable for upstreaming."

DEPENDS_append = " bc-native dtc-native lzop-native"

# Location known to imx-boot component, where U-Boot artifacts
# should be additionally deployed.
# See below note above do_deploy_append_mx8m for the purpose of
# this delopyment location
BOOT_TOOLS = "imx-boot-tools"

DEPENDS_append_boot-container = " \
    ${IMX_EXTRA_FIRMWARE} \
    imx-atf \
    ${@bb.utils.contains('MACHINE_FEATURES', 'optee', 'optee-os', '', d)} \
"

# This package aggregates output deployed by other packages,
# so set the appropriate dependencies
do_compile[depends] += " \
    ${@' '.join('%s:do_deploy' % r for r in '${IMX_EXTRA_FIRMWARE}'.split() )} \
    imx-atf:do_deploy \
    ${@bb.utils.contains('MACHINE_FEATURES', 'optee', 'optee-os:do_deploy', '', d)} \
"

ATF_MACHINE_NAME = "bl31-${ATF_PLATFORM}.bin"
ATF_MACHINE_NAME_append = "${@bb.utils.contains('MACHINE_FEATURES', 'optee', '-optee', '', d)}"

PROVIDES += "u-boot"

B = "${WORKDIR}/build"

# FIXME: Allow linking of 'tools' binaries with native libraries
#        used for generating the boot logo and other tools used
#        during the build process.
EXTRA_OEMAKE += 'HOSTCC="${BUILD_CC} ${BUILD_CPPFLAGS}" \
                 HOSTLDFLAGS="${BUILD_LDFLAGS}" \
                 HOSTSTRIP=true'

#
# imx8m machines require a separate build target to be executed
# due to the fact that final boot image is constructed using flash.bin
# taget. It produces a boot binary image, which is constructed from
# various binary components (u-boot with separate dtb, atf, DDR
# firmware and optional op-tee) into a single image using FIT format.
# This flash.bin file is then parsed and loaded either via
# SPL directly (imx8mm), or using bootrom code (imx8mn and imx8mp).
#
# In order to use flash.bin binary boot image, it is required that
# the U-Boot build is to be invoked for an additional build target.
do_compile_prepend_boot-container() {
    if [ -n "${UBOOT_CONFIG}" ]; then
        for config in ${UBOOT_MACHINE}; do
            i=$(expr $i + 1);
            for type in ${UBOOT_CONFIG}; do
                j=$(expr $j + 1);
                if [ $j -eq $i ]; then
                    for ddr_firmware in ${DDR_FIRMWARE_NAME}; do
                        # Sanitize the FW name as U-Boot expects it to be without version
                        if [ -n "${DDR_FIRMWARE_VERSION}" ]; then
                            ddr_firmware_name=$(echo $ddr_firmware | sed s/_${DDR_FIRMWARE_VERSION}//)
                        else
                            ddr_firmware_name="$ddr_firmware"
                        fi
                        bbnote "Copy ddr_firmware: ${ddr_firmware} from ${DEPLOY_DIR_IMAGE} -> ${B}/${config}/${ddr_firmware_name}"
                        cp ${DEPLOY_DIR_IMAGE}/${ddr_firmware} ${B}/${config}/${ddr_firmware_name}
                    done
                    if [ -n "${ATF_MACHINE_NAME}" ]; then
                        cp ${DEPLOY_DIR_IMAGE}/${BOOT_TOOLS}/${ATF_MACHINE_NAME} ${B}/${config}/bl31.bin
                    else
                        bberror "ATF binary is undefined, result binary would be unusable!"
                    fi
                    # Run compile pass to produce extra boot container, which is
                    # done via separate target
                    if [ ! -n "${ATF_LOAD_ADDR}" ]; then
                        bberror "ATF_LOAD_ADDR is undefined, result binary would be unusable!"
                    fi
                    export ATF_LOAD_ADDR=${ATF_LOAD_ADDR}
                    oe_runmake -C ${S} O=${B}/${config} flash.bin
                fi
            done
            unset  j
        done
        unset  i
    fi
}

do_deploy_append_boot-container() {
    # Deploy the resulted flash.bin for WIC to pick it up
    if [ -n "${UBOOT_CONFIG}" ]; then
        for config in ${UBOOT_MACHINE}; do
            i=$(expr $i + 1);
            for type in ${UBOOT_CONFIG}; do
                j=$(expr $j + 1);
                if [ $j -eq $i ]
                then
                    install -m 0777 ${B}/${config}/flash.bin  ${DEPLOYDIR}/flash.bin-${MACHINE}-${UBOOT_CONFIG}
                    ln -sf flash.bin-${MACHINE}-${UBOOT_CONFIG} flash.bin
                fi
            done
            unset  j
        done
        unset  i
    fi
}


PACKAGE_ARCH = "${MACHINE_ARCH}"
COMPATIBLE_MACHINE = "(mxs|mx5|mx6|mx7|vf|use-mainline-bsp)"
