package com.quiddity.app.active

import android.os.Parcel
import android.os.Parcelable

/**
 * Shizuku 用户服务执行结果（AIDL Parcelable）。
 * [exitCode] 为进程退出码；[stdout] 与 [stderr] 为命令输出。
 */
class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readString().orEmpty(),
        parcel.readString().orEmpty()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(exitCode)
        parcel.writeString(stdout)
        parcel.writeString(stderr)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ShellResult> = object : Parcelable.Creator<ShellResult> {
            override fun createFromParcel(parcel: Parcel): ShellResult = ShellResult(parcel)
            override fun newArray(size: Int): Array<ShellResult?> = arrayOfNulls(size)
        }
    }
}
