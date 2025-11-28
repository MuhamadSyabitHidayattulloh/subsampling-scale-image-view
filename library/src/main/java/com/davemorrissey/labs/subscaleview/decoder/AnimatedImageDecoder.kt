package com.davemorrissey.labs.subscaleview.decoder

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import com.davemorrissey.labs.subscaleview.internal.URI_PATH_ASSET
import com.davemorrissey.labs.subscaleview.internal.URI_SCHEME_CONTENT
import com.davemorrissey.labs.subscaleview.internal.URI_SCHEME_FILE
import com.davemorrissey.labs.subscaleview.internal.URI_SCHEME_RES
import com.davemorrissey.labs.subscaleview.internal.URI_SCHEME_ZIP
import org.jetbrains.annotations.Blocking
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipFile

public data class AnimatedImage(
	public val drawable: Drawable,
	public val width: Int,
	public val height: Int,
)

public fun interface AnimatedImageDecoder {

	@WorkerThread
	@Blocking
	@Throws(Exception::class)
	public fun decode(context: Context, uri: Uri): AnimatedImage?
}

@RequiresApi(Build.VERSION_CODES.P)
public class ImageDecoderAnimatedImageDecoder @JvmOverloads constructor(
	private val allocator: Int = ImageDecoder.ALLOCATOR_SOFTWARE,
) : AnimatedImageDecoder {

	override fun decode(context: Context, uri: Uri): AnimatedImage? {
		val source = createSource(context, uri) ?: return null
		var info: ImageDecoder.ImageInfo? = null
		val drawable = ImageDecoder.decodeDrawable(source) { decoder, imageInfo, _ ->
			info = imageInfo
			decoder.allocator = allocator
		}
		if (drawable !is AnimatedImageDrawable) {
			return null
		}
		val size = info?.size ?: return null
		return AnimatedImage(drawable, size.width, size.height)
	}

	private fun createSource(context: Context, uri: Uri): ImageDecoder.Source? = when (uri.scheme) {
		URI_SCHEME_RES -> {
			val (resources, resId) = resolveResource(context, uri) ?: return null
			ImageDecoder.createSource(resources, resId)
		}

		URI_SCHEME_ZIP -> {
			val entryName = uri.fragment ?: return null
			ZipFile(uri.schemeSpecificPart).use { file ->
				val entry = file.getEntry(entryName) ?: return null
				file.getInputStream(entry).use { input ->
					ImageDecoder.createSource(ByteBuffer.wrap(input.readBytes()))
				}
			}
		}

		URI_SCHEME_FILE -> {
			val path = uri.schemeSpecificPart
			if (path.startsWith(URI_PATH_ASSET, ignoreCase = true)) {
				val assetName = path.substring(URI_PATH_ASSET.length)
				ImageDecoder.createSource(context.assets, assetName)
			} else {
				ImageDecoder.createSource(File(path))
			}
		}

		URI_SCHEME_CONTENT -> ImageDecoder.createSource(context.contentResolver, uri)

		else -> null
	}

	@SuppressLint("DiscouragedApi")
	private fun resolveResource(context: Context, uri: Uri): Pair<Resources, Int>? {
		val packageName = uri.authority ?: context.packageName
		val resources = if (packageName == context.packageName) {
			context.resources
		} else {
			context.packageManager.getResourcesForApplication(packageName)
		}
		val segments = uri.pathSegments
		val resourceId = when {
			segments.size == 2 && segments[0] == "drawable" -> resources.getIdentifier(segments[1], "drawable", packageName)
			segments.size == 1 -> segments[0].toIntOrNull() ?: 0
			else -> 0
		}
		return if (resourceId != 0) resources to resourceId else null
	}

	public class Factory @JvmOverloads constructor(
		private val allocator: Int = ImageDecoder.ALLOCATOR_SOFTWARE,
	) : DecoderFactory<ImageDecoderAnimatedImageDecoder> {

		override fun make(): ImageDecoderAnimatedImageDecoder {
			return ImageDecoderAnimatedImageDecoder(allocator)
		}
	}
}

