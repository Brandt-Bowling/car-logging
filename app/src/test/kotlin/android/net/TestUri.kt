package android.net

import android.os.Parcel
import java.net.URI

fun testUri(raw: String): Uri = TestUri(raw)

private class TestUri(private val raw: String) : Uri() {
    private val parsed: URI = URI.create(raw)

    override fun isHierarchical(): Boolean = !parsed.isOpaque

    override fun isRelative(): Boolean = !parsed.isAbsolute

    override fun getScheme(): String? = parsed.scheme

    override fun getSchemeSpecificPart(): String? = parsed.schemeSpecificPart

    override fun getEncodedSchemeSpecificPart(): String? = parsed.rawSchemeSpecificPart

    override fun getAuthority(): String? = parsed.authority

    override fun getEncodedAuthority(): String? = parsed.rawAuthority

    override fun getUserInfo(): String? = parsed.userInfo

    override fun getEncodedUserInfo(): String? = parsed.rawUserInfo

    override fun getHost(): String? = parsed.host

    override fun getPort(): Int = parsed.port

    override fun getPath(): String? = parsed.path

    override fun getEncodedPath(): String? = parsed.rawPath

    override fun getQuery(): String? = parsed.query

    override fun getEncodedQuery(): String? = parsed.rawQuery

    override fun getFragment(): String? = parsed.fragment

    override fun getEncodedFragment(): String? = parsed.rawFragment

    override fun getPathSegments(): MutableList<String> {
        val currentPath = parsed.path ?: return mutableListOf()
        return currentPath.split('/').filter { it.isNotEmpty() }.toMutableList()
    }

    override fun getLastPathSegment(): String? = getPathSegments().lastOrNull()

    override fun buildUpon(): Builder {
        throw UnsupportedOperationException("TestUri.buildUpon() is not supported on pure JVM")
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        // No-op in pure JVM tests
    }

    override fun toString(): String = raw

    override fun equals(other: Any?): Boolean = other is Uri && toString() == other.toString()

    override fun hashCode(): Int = raw.hashCode()
}
