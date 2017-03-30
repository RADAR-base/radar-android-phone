package org.radarcns.phone;

import android.os.Parcelable;

import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceServiceProvider;

import java.util.Arrays;
import java.util.List;

import static android.Manifest.permission.READ_CALL_LOG;
import static android.Manifest.permission.READ_SMS;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class PhoneLogProvider extends DeviceServiceProvider<BaseDeviceState> {
    @Override
    public Class<?> getServiceClass() {
        return PhoneLogService.class;
    }

    @Override
    public Parcelable.Creator<BaseDeviceState> getStateCreator() {
        return BaseDeviceState.CREATOR;
    }

    @Override
    public String getDisplayName() {
        return getActivity().getString(R.string.phoneLogServiceDisplayName);
    }

    @Override
    public List<String> needsPermissions() {
        return Arrays.asList(WRITE_EXTERNAL_STORAGE, READ_CALL_LOG, READ_SMS);
    }

    @Override
    public boolean isDisplayable() {
        return false;
    }
}
