package com.example.lcb.app.streetview

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.lcb.app.R
import com.google.android.gms.maps.model.LatLng

/** 热门街景与搜索推荐共用同一份地标数据，避免名称、坐标和封面不一致。 */
internal object HotSpotCatalog {
    private data class Landmark(
        @param:StringRes val title: Int,
        @param:StringRes val subtitle: Int,
        @param:StringRes val description: Int,
        val latitude: Double,
        val longitude: Double,
        @param:DrawableRes val cover: Int
    )

    private val landmarks = listOf(
        Landmark(R.string.hot_eiffel_title, R.string.hot_eiffel_sub, R.string.hot_eiffel_desc,
            48.8584, 2.2945, R.drawable.hot_eiffel),
        Landmark(R.string.hot_times_title, R.string.hot_times_sub, R.string.hot_times_desc,
            40.7580, -73.9855, R.drawable.hot_times),
        Landmark(R.string.hot_bigben_title, R.string.hot_bigben_sub, R.string.hot_bigben_desc,
            51.5007, -0.1246, R.drawable.hot_bigben),
        Landmark(R.string.hot_colosseum_title, R.string.hot_colosseum_sub, R.string.hot_colosseum_desc,
            41.8902, 12.4922, R.drawable.hot_colosseum),
        Landmark(R.string.hot_sydney_title, R.string.hot_sydney_sub, R.string.hot_sydney_desc,
            -33.8568, 151.2153, R.drawable.hot_sydney),
        Landmark(R.string.hot_shibuya_title, R.string.hot_shibuya_sub, R.string.hot_shibuya_desc,
            35.6595, 139.7004, R.drawable.hot_shibuya),
        Landmark(R.string.hot_goldengate_title, R.string.hot_goldengate_sub, R.string.hot_goldengate_desc,
            37.8199, -122.4783, R.drawable.hot_goldengate),
        Landmark(R.string.hot_sagrada_title, R.string.hot_sagrada_sub, R.string.hot_sagrada_desc,
            41.4036, 2.1744, R.drawable.hot_sagrada)
    )

    /** 根据当前语言生成展示文本，封面始终使用随应用打包的资源。 */
    fun build(context: Context): List<StreetViewSpot> = landmarks.map { landmark ->
        StreetViewSpot(
            title = context.getString(landmark.title),
            subtitle = context.getString(landmark.subtitle),
            position = LatLng(landmark.latitude, landmark.longitude),
            date = null,
            distanceMeters = null,
            coverRes = landmark.cover,
            description = context.getString(landmark.description)
        )
    }
}
