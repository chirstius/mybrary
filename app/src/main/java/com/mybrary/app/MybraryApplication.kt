package com.mybrary.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.mybrary.app.data.remote.DriveCoverFetcher
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MybraryApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(DriveCoverFetcher.Factory()) }
            .build()
}
