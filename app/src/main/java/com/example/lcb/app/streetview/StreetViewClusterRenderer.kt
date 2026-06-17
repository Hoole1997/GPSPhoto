package com.example.lcb.app.streetview

import android.content.Context
import android.widget.TextView
import com.example.lcb.app.R
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.ui.IconGenerator

/**
 * Custom renderer: pretty circular count badge for clusters, source-colored
 * pins for individual street view points.
 */
class StreetViewClusterRenderer(
    context: Context,
    map: GoogleMap,
    clusterManager: ClusterManager<StreetViewClusterItem>
) : DefaultClusterRenderer<StreetViewClusterItem>(context, map, clusterManager) {

    private val iconGenerator = IconGenerator(context)
    private val clusterView = android.view.LayoutInflater.from(context)
        .inflate(R.layout.marker_cluster, null)
    private val countText: TextView = clusterView.findViewById(R.id.clusterCount)

    private val cache = HashMap<String, BitmapDescriptor>()

    init {
        iconGenerator.setContentView(clusterView)
        iconGenerator.setBackground(null)
    }

    override fun onBeforeClusterItemRendered(
        item: StreetViewClusterItem,
        markerOptions: MarkerOptions
    ) {
        val hue = if (item.spot.source == StreetViewSource.MAPILLARY) {
            BitmapDescriptorFactory.HUE_GREEN
        } else {
            BitmapDescriptorFactory.HUE_AZURE
        }
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(hue))
            .title(item.spot.title)
    }

    override fun onBeforeClusterRendered(
        cluster: Cluster<StreetViewClusterItem>,
        markerOptions: MarkerOptions
    ) {
        markerOptions.icon(clusterIcon(cluster.size))
    }

    private fun clusterIcon(size: Int): BitmapDescriptor {
        val label = if (size > 99) "99+" else size.toString()
        cache[label]?.let { return it }
        countText.text = label
        val bitmap = iconGenerator.makeIcon()
        val descriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
        cache[label] = descriptor
        return descriptor
    }

    override fun shouldRenderAsCluster(cluster: Cluster<StreetViewClusterItem>): Boolean {
        // 2 个及以上才聚合
        return cluster.size >= 2
    }
}
