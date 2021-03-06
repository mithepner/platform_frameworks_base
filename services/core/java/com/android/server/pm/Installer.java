/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.server.pm;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageStats;
import android.os.Build;
import android.os.IInstalld;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.util.Slog;

import com.android.internal.os.InstallerConnection;
import com.android.internal.os.InstallerConnection.InstallerException;
import com.android.server.SystemService;

import dalvik.system.VMRuntime;

import java.util.Arrays;

public class Installer extends SystemService {
    private static final String TAG = "Installer";

    /* ***************************************************************************
     * IMPORTANT: These values are passed to native code. Keep them in sync with
     * frameworks/native/cmds/installd/installd.h
     * **************************************************************************/
    /** Application should be visible to everyone */
    public static final int DEXOPT_PUBLIC         = 1 << 1;
    /** Application wants to run in VM safe mode */
    public static final int DEXOPT_SAFEMODE       = 1 << 2;
    /** Application wants to allow debugging of its code */
    public static final int DEXOPT_DEBUGGABLE     = 1 << 3;
    /** The system boot has finished */
    public static final int DEXOPT_BOOTCOMPLETE   = 1 << 4;
    /** Hint that the dexopt type is profile-guided. */
    public static final int DEXOPT_PROFILE_GUIDED = 1 << 5;
    /** This is an OTA update dexopt */
    public static final int DEXOPT_OTA            = 1 << 6;

    // NOTE: keep in sync with installd
    public static final int FLAG_CLEAR_CACHE_ONLY = 1 << 8;
    public static final int FLAG_CLEAR_CODE_CACHE_ONLY = 1 << 9;

    private final boolean mIsolated;

    // TODO: reconnect if installd restarts
    private final InstallerConnection mInstaller;
    private final IInstalld mInstalld;

    private volatile Object mWarnIfHeld;

    public Installer(Context context) {
        this(context, false);
    }

    /**
     * @param isolated indicates if this object should <em>not</em> connect to
     *            the real {@code installd}. All remote calls will be ignored
     *            unless you extend this class and intercept them.
     */
    public Installer(Context context, boolean isolated) {
        super(context);
        mIsolated = isolated;
        if (isolated) {
            mInstaller = null;
            mInstalld = null;
        } else {
            mInstaller = new InstallerConnection();
            mInstalld = IInstalld.Stub.asInterface(ServiceManager.getService("installd"));
        }
    }

    /**
     * Yell loudly if someone tries making future calls while holding a lock on
     * the given object.
     */
    public void setWarnIfHeld(Object warnIfHeld) {
        if (mInstaller != null) {
            mInstaller.setWarnIfHeld(warnIfHeld);
        }
        mWarnIfHeld = warnIfHeld;
    }

    @Override
    public void onStart() {
        if (mInstaller != null) {
            Slog.i(TAG, "Waiting for installd to be ready.");
            mInstaller.waitForConnection();
        }
    }

    /**
     * Do several pre-flight checks before making a remote call.
     *
     * @return if the remote call should continue.
     */
    private boolean checkBeforeRemote() {
        if (mWarnIfHeld != null && Thread.holdsLock(mWarnIfHeld)) {
            Slog.wtf(TAG, "Calling thread " + Thread.currentThread().getName() + " is holding 0x"
                    + Integer.toHexString(System.identityHashCode(mWarnIfHeld)), new Throwable());
        }
        if (mIsolated) {
            Slog.i(TAG, "Ignoring request because this installer is isolated");
            return false;
        } else {
            return true;
        }
    }

    public void createAppData(String uuid, String packageName, int userId, int flags, int appId,
            String seInfo, int targetSdkVersion) throws InstallerException {
        if (!checkBeforeRemote()) return;
        try {
            mInstalld.createAppData(uuid, packageName, userId, flags, appId, seInfo,
                    targetSdkVersion);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public void restoreconAppData(String uuid, String packageName, int userId, int flags, int appId,
            String seInfo) throws InstallerException {
        if (!checkBeforeRemote()) return;
        try {
            mInstalld.restoreconAppData(uuid, packageName, userId, flags, appId, seInfo);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public void migrateAppData(String uuid, String packageName, int userId, int flags)
            throws InstallerException {
        if (!checkBeforeRemote()) return;
        try {
            mInstalld.migrateAppData(uuid, packageName, userId, flags);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public void clearAppData(String uuid, String packageName, int userId, int flags,
            long ceDataInode) throws InstallerException {
        if (!checkBeforeRemote()) return;
        try {
            mInstalld.clearAppData(uuid, packageName, userId, flags, ceDataInode);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public void destroyAppData(String uuid, String packageName, int userId, int flags,
            long ceDataInode) throws InstallerException {
        if (!checkBeforeRemote()) return;
        try {
            mInstalld.destroyAppData(uuid, packageName, userId, flags, ceDataInode);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public void moveCompleteApp(String fromUuid, String toUuid, String packageName,
            String dataAppName, int appId, String seInfo, int targetSdkVersion)
            throws InstallerException {
        if (!checkBeforeRemote()) return;
        try {
            mInstalld.moveCompleteApp(fromUuid, toUuid, packageName, dataAppName, appId, seInfo,
                    targetSdkVersion);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public void getAppSize(String uuid, String pkgname, int userid, int flags, long ceDataInode,
            String codePath, PackageStats stats) throws InstallerException {
        if (!checkBeforeRemote()) return;
        final String[] res = mInstaller.execute("get_app_size", uuid, pkgname, userid, flags,
                ceDataInode, codePath);
        try {
            stats.codeSize += Long.parseLong(res[1]);
            stats.dataSize += Long.parseLong(res[2]);
            stats.cacheSize += Long.parseLong(res[3]);
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            throw new InstallerException("Invalid size result: " + Arrays.toString(res));
        }
    }

    public long getAppDataInode(String uuid, String packageName, int userId, int flags)
            throws InstallerException {
        if (!checkBeforeRemote()) return -1;
        try {
            return mInstalld.getAppDataInode(uuid, packageName, userId, flags);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public void dexopt(String apkPath, int uid, @Nullable String pkgName, String instructionSet,
            int dexoptNeeded, @Nullable String outputPath, int dexFlags,
            String compilerFilter, @Nullable String volumeUuid, @Nullable String sharedLibraries)
            throws InstallerException {
        assertValidInstructionSet(instructionSet);
        if (!checkBeforeRemote()) return;
        mInstaller.dexopt(apkPath, uid, pkgName, instructionSet, dexoptNeeded,
                outputPath, dexFlags, compilerFilter, volumeUuid, sharedLibraries);
    }

    public boolean mergeProfiles(int uid, String packageName) throws InstallerException {
        if (!checkBeforeRemote()) return false;
        try {
            return mInstalld.mergeProfiles(uid, packageName);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public boolean dumpProfiles(int uid, String packageName, String codePaths)
            throws InstallerException {
        if (!checkBeforeRemote()) return false;
        try {
            return mInstalld.dumpProfiles(uid, packageName, codePaths);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public void idmap(String targetApkPath, String overlayApkPath, int uid)
            throws InstallerException {
        if (!checkBeforeRemote()) return;
        try {
            mInstalld.idmap(targetApkPath, overlayApkPath, uid);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public void rmdex(String codePath, String instructionSet) throws InstallerException {
        assertValidInstructionSet(instructionSet);
        if (!checkBeforeRemote()) return;
        try {
            mInstalld.rmdex(codePath, instructionSet);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public void rmPackageDir(String packageDir) throws InstallerException {
        if (!checkBeforeRemote()) return;
        try {
            mInstalld.rmPackageDir(packageDir);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public void clearAppProfiles(String packageName) throws InstallerException {
        if (!checkBeforeRemote()) return;
        try {
            mInstalld.clearAppProfiles(packageName);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public void destroyAppProfiles(String packageName) throws InstallerException {
        if (!checkBeforeRemote()) return;
        try {
            mInstalld.destroyAppProfiles(packageName);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public void createUserData(String uuid, int userId, int userSerial, int flags)
            throws InstallerException {
        if (!checkBeforeRemote()) return;
        try {
            mInstalld.createUserData(uuid, userId, userSerial, flags);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public void destroyUserData(String uuid, int userId, int flags) throws InstallerException {
        if (!checkBeforeRemote()) return;
        try {
            mInstalld.destroyUserData(uuid, userId, flags);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public void markBootComplete(String instructionSet) throws InstallerException {
        assertValidInstructionSet(instructionSet);
        if (!checkBeforeRemote()) return;
        try {
            mInstalld.markBootComplete(instructionSet);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public void freeCache(String uuid, long freeStorageSize) throws InstallerException {
        if (!checkBeforeRemote()) return;
        try {
            mInstalld.freeCache(uuid, freeStorageSize);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    /**
     * Links the 32 bit native library directory in an application's data
     * directory to the real location for backward compatibility. Note that no
     * such symlink is created for 64 bit shared libraries.
     */
    public void linkNativeLibraryDirectory(String uuid, String packageName, String nativeLibPath32,
            int userId) throws InstallerException {
        if (!checkBeforeRemote()) return;
        try {
            mInstalld.linkNativeLibraryDirectory(uuid, packageName, nativeLibPath32, userId);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public void createOatDir(String oatDir, String dexInstructionSet)
            throws InstallerException {
        if (!checkBeforeRemote()) return;
        try {
            mInstalld.createOatDir(oatDir, dexInstructionSet);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public void linkFile(String relativePath, String fromBase, String toBase)
            throws InstallerException {
        if (!checkBeforeRemote()) return;
        try {
            mInstalld.linkFile(relativePath, fromBase, toBase);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public void moveAb(String apkPath, String instructionSet, String outputPath)
            throws InstallerException {
        if (!checkBeforeRemote()) return;
        try {
            mInstalld.moveAb(apkPath, instructionSet, outputPath);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    public void deleteOdex(String apkPath, String instructionSet, String outputPath)
            throws InstallerException {
        if (!checkBeforeRemote()) return;
        try {
            mInstalld.deleteOdex(apkPath, instructionSet, outputPath);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new InstallerException(e.getMessage());
        }
    }

    private static void assertValidInstructionSet(String instructionSet)
            throws InstallerException {
        for (String abi : Build.SUPPORTED_ABIS) {
            if (VMRuntime.getInstructionSet(abi).equals(instructionSet)) {
                return;
            }
        }
        throw new InstallerException("Invalid instruction set: " + instructionSet);
    }
}
