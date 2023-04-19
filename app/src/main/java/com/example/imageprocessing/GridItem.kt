package com.example.imageprocessing

import android.graphics.Bitmap
import android.net.Uri
import androidx.recyclerview.widget.DiffUtil

data class GridItem(var image: Uri, var qualityScoreText: String?, var numberOfFacesText: String?, var similarityScoreText: String?)
