package com.example.imageprocessing

//import androidx.camera.core.*
//import org.opencv.android.Utils
//import org.opencv.core.*
//import org.opencv.imgproc.Imgproc
import android.Manifest
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.imageprocessing.R
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.example.imageprocessing.GridAdapter
import com.example.imageprocessing.GridItem
import com.example.imageprocessing.databinding.FaceDetectionFragmentBinding
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark.*
import kotlinx.android.synthetic.main.face_detection_fragment.*
import kotlinx.android.synthetic.main.image_item.*
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt



class FaceDetectionFragment: Fragment(), ActivityCompat.OnRequestPermissionsResultCallback {

    private lateinit var binding:FaceDetectionFragmentBinding
    private val REQUEST_CODE_PICK_IMAGES = 100


    //Keeps track of images with less or more than one face
    private var nonOneFaceImages: ArrayList<Int> = ArrayList()
    //Stores image URIs of all images picked from gallery
    private var selectedImages: MutableList<Uri> = mutableListOf()
    //Keeps track of faces found in each image(stores 0 in case there is less than or more than 1 face)
    private var faceCount: MutableList<Int> = mutableListOf()

    //Face recognition model
    private lateinit var tflite: Interpreter

    //Map to store checks
    private var checkMap = mutableMapOf("Quality" to false, "Detection" to false, "Recognition" to false)

    //Grid Items list for recycler view
    var gridItems = mutableListOf<GridItem>()

    //Stores embedding of the reference face captured via Camera
    private lateinit var referenceFaceEmbedding: FloatArray

    //CameraX based objects
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraProvider: ProcessCameraProvider



    //Not used currently
    private lateinit var canvas: Canvas
    private var imageAnalysis: ImageAnalysis? = null
    private lateinit var cameraExecutor: ExecutorService
    lateinit var currentPhotoPath: String
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private val REQUEST_IMAGE_PICK = 1

    // Not used currently - Define constants for quality factor weights
    val RESOLUTION_WEIGHT = 0.2f
    val BRIGHTNESS_WEIGHT = 0.3f
    val CONTRAST_WEIGHT = 0.1f
    val SHARPNESS_WEIGHT = 0.5f
    val COLOR_BALANCE_WEIGHT = 0.1f


    companion object {
        const val TAG= "FaceDetectionFragment"
        private const val REQUEST_CODE_PERMISSIONS_CAMERA = 1
        private const val REQUEST_CODE_READ_EXTERNAL_STORAGE = 2
        private val REQUIRED_PERMISSIONS_CAMERA = arrayOf(Manifest.permission.CAMERA)
        private  val REQUIRED_PERMISSIONS_STORAGE = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)

//        Load the facenet.tflite model from assets folder
//        val model = loadModelFile(requireContext().assets, "facenet.tflite")
//
//        // Create an Interpreter instance to run inference with the loaded model
//        val options = Interpreter.Options()
//        interpreter = Interpreter(model, options)


        //mobile facenet
        val model = loadModelFile(requireActivity(), "facenet.tflite")
        val options = Interpreter.Options()
        tflite = Interpreter(model, options)



        binding= DataBindingUtil.inflate(inflater, R.layout.face_detection_fragment, container, false)
        binding.lifecycleOwner = activity
        return binding.root

    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up the RecyclerView
        val layoutManager = GridLayoutManager(context, 3)
        grid_recycler_view.layoutManager = layoutManager
        val adapter = GridAdapter(requireContext(), gridItems)
        grid_recycler_view.adapter = adapter

//        detect_faces.setOnClickListener { detectFaces() }
        select_image.setOnClickListener { selectImage() }

        capture_image_button.setOnClickListener {
            imageCapture?.takePicture(
                ContextCompat.getMainExecutor(requireContext()),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        cameraProvider.unbindAll()
                        select_image.isEnabled = true


                        //Handling in case we receive rotated image
//                        var rotation = image.imageInfo.rotationDegrees
//                        val bitmapRotation = when (rotation) {
//                            0 -> 0f
//                            90 -> 90f
//                            180 -> 180f
//                            270 -> 270f
//                            else -> throw IllegalArgumentException("Invalid rotation degrees")
//                        }
//
//                        val buffer = image.planes[0].buffer
//                        val bytes = ByteArray(buffer.remaining())
//                        buffer.get(bytes)

//
//                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
//
//                        val matrix = Matrix().apply {
//                            postRotate(bitmapRotation)
//                        }
//
//                        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)


                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)

                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

//                         Create a matrix to rotate the Bitmap by 90 degrees (Manually rotating Image to check rotation)
//                        val matrix = Matrix().apply { postRotate(90f) }
//                         Rotate the Bitmap
//                        val rotatedBitmap = Bitmap.createBitmap(bitmap!!, 0, 0, bitmap.width, bitmap.height, matrix, true)


//                        captured_image.setImageBitmap(rotatedBitmap)
//                        captured_image.visibility =View.VISIBLE

                        //Handling Rotation
                        var orientation = ExifInterface.ORIENTATION_NORMAL
                        if(image.imageInfo.rotationDegrees == 90) {
                            Toast.makeText(context, "Rotation 90", Toast.LENGTH_SHORT).show()
                            orientation = ExifInterface.ORIENTATION_ROTATE_90
                        }
                        else if(image.imageInfo.rotationDegrees == 180) {
                            Toast.makeText(context, "Rotation 180", Toast.LENGTH_SHORT).show()
                            orientation = ExifInterface.ORIENTATION_ROTATE_180
                        }
                        else if(image.imageInfo.rotationDegrees == 270) {
                            Toast.makeText(context, "Rotation 270", Toast.LENGTH_SHORT).show()
                            orientation = ExifInterface.ORIENTATION_ROTATE_270
                        }
                        else {
                            Toast.makeText(context, "Rotation Normal", Toast.LENGTH_SHORT).show()
                        }


                        // Rotate the Bitmap to the correct orientation
                       val matrixRotate = Matrix()
                        when (orientation) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> matrixRotate.postRotate(90f)
                            ExifInterface.ORIENTATION_ROTATE_180 -> matrixRotate.postRotate(180f)
                            ExifInterface.ORIENTATION_ROTATE_270 -> matrixRotate.postRotate(270f)
                            ExifInterface.ORIENTATION_NORMAL -> matrixRotate.postRotate(0f)
                        }

                        val correctRotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrixRotate, true)

                        captured_image.setImageBitmap(correctRotatedBitmap)
                        captured_image.visibility =View.VISIBLE


//                        Flipping along x-axis
//                        val matrix = Matrix().apply {
//                            postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
//                        }
//
//                        //Flipping along x-axis since captured image comes flipped
//                        val flippedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)


                        var resizedBitmap = correctRotatedBitmap

                        if(bitmap.width > 512 && bitmap.height > 512)
                            resizedBitmap = resizeBitmap(correctRotatedBitmap, 512,512)

                        val mutableBitmap = resizedBitmap.copy(Bitmap.Config.ARGB_8888, true)
                        var qualityScore = calculateImageQualityScore(mutableBitmap)


                        val qualityScoreTwoDecimals = String.format("%.2f", qualityScore)
                        if(qualityScore > 2.75) {
                            Toast.makeText(context, "reference image quality: $qualityScoreTwoDecimals", Toast.LENGTH_SHORT).show()
                            singleImageFaceDetection(mutableBitmap)
                        }
                        else {
//                            Toast.makeText(
//                                context,
//                                "Quality low $qualityScoreTwoDecimals",
//                                Toast.LENGTH_SHORT
//                            ).show()

                            val builder = AlertDialog.Builder(requireContext())
                            builder.setTitle("Image quality low")
                            builder.setMessage("Quality low $qualityScoreTwoDecimals")
                            builder.setPositiveButton("OK") { dialog, which ->
                                dialog.dismiss() // Close the dialog
                            }
                            builder.show()
                            image_batch_check_button.isEnabled = false
                        }

                        view_finder.visibility = View.GONE
//                        capture_image_button.visibility = View.GONE

                        recapture_image_button.visibility = View.VISIBLE
                        recapture_image_button.setOnClickListener {
                            imageCapture = null
                            view_finder.visibility = View.VISIBLE
                            capture_image_button.visibility = View.VISIBLE
                            recapture_image_button.visibility = View.GONE
                            view_finder.post {
                                beginPreview()
                            }
                        }

//                        Toast.makeText(context, "HEIGHT ${bitmap.height} WIDTH ${bitmap.width}", Toast.LENGTH_SHORT).show()
                        capture_image_button.visibility = View.GONE
                        super.onCaptureSuccess(image)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Image capture failed: ${exception.message}", exception)
                        super.onError(exception)
                    }
                })
        }


        //Live Camera Code

//        cameraExecutor = Executors.newSingleThreadExecutor()
//
//        if (allPermissionsGranted()) {
//            startCamera()
//        } else {
//            ActivityCompat.requestPermissions(requireActivity(), REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
//        }
//
//        facing_switch.setOnCheckedChangeListener {_, isChecked ->
//            lensFacing = if (isChecked) {
//                CameraSelector.LENS_FACING_FRONT
//            } else {
//                CameraSelector.LENS_FACING_BACK
//            }
//            startCamera()
//        }
    }


    //Select Image button click function
    private fun selectImage() {
        //To ensure adapter is clear whenever adding images
        selectedImages.clear()
//        gridItems = mutableListOf<GridItem>()
        gridItems.clear()
        grid_recycler_view.adapter?.notifyDataSetChanged()
        check_boxes.visibility = View.GONE
        start_camera_button.visibility = View.GONE
        image_batch_check_button.isEnabled = false
        image_quality_text.text = "Image Quality Check - Not Done Yet"
        face_detection_text.text = "Face Detection Check - Not Done Yet"
        face_recognition_text.text = "Face Recognition Check - Not Done Yet"
//        quality_check_button.isEnabled = false
//        face_detection_button.isEnabled = false
//        face_recognition_button.isEnabled = false


//        //For picking multiple images

        val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(pickIntent, REQUEST_CODE_PICK_IMAGES)


        //For single pick
//        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
//        startActivityForResult(intent, REQUEST_IMAGE_PICK)
    }

    //NOT USED ANYMORE - Below code uses Native Camera library of android which is deprecated
    private fun startCameraFunc() {

//        if (allPermissionsGranted()) {
//            startCamera()
//        } else {
//            ActivityCompat.requestPermissions(requireActivity(), REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
//        }

        //Below code uses Native Camera library of android which is deprecated
        //For Thumbnail Image
//        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
//        if (takePictureIntent.resolveActivity(requireActivity().packageManager) != null) {
//            startActivityForResult(takePictureIntent, 1)
//        }

        //For full size photo - First way

//        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
//        val imageFile = File(requireActivity().externalCacheDir, "captured_image.jpg")
//        val imageUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", imageFile)
//        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
//        startActivityForResult(intent, 1)



        //For Full size photo -  Second way

//        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
//            // Ensure that there's a camera activity to handle the intent
//            takePictureIntent.resolveActivity(requireContext().packageManager)?.also {
//                // Create the File where the photo should go
//                val photoFile: File? = try {
//                    createImageFile()
//                } catch (ex: IOException) {
//                    // Error occurred while creating the File
//                    null
//                }
//                // Continue only if the File was successfully created
//                photoFile?.also {
//                    val photoURI: Uri = FileProvider.getUriForFile(
//                        requireContext(),
//                        "com.doppellife.fileprovider",
//                        it
//                    )
//                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
//                    startActivityForResult(takePictureIntent, 1)
//                }
//            }
//        }
    }

    //NOT USED ANYMORE - Below code was for when we were using deprecated Camera library of android to store image on capture in gallery
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }


    //This runs when after images are selected from gallery
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        //For multiple images
//        if (requestCode == REQUEST_CODE_PICK_IMAGES && resultCode == Activity.RESULT_OK) {
//
//            val selectedImages = mutableListOf<Uri>()
//
//            // Check if the selected images fall within the desired range
//            if (data?.clipData?.itemCount ?: 0 in 3..5) {
//                // Retrieve the selected images
//                if (data?.clipData != null) {
//                    for (i in 0 until data.clipData!!.itemCount) {
//                        selectedImages.add(data.clipData!!.getItemAt(i).uri)
//                    }
//                } else if (data?.data != null) {
//                    selectedImages.add(data.data!!)
//                }
//
//                // Do something with the selected images
//                // ...
//
//                view_pager_select_images.adapter = ImagePagerAdapter(selectedImages)
//            } else {
//                Toast.makeText(context, "Please select between 3 and 5 images", Toast.LENGTH_SHORT).show()
//            }
//        }

//       NOT USED ANYMORE - For clicking with native Camera(deprecated in android)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            //use to detect face and then create face embeddings
            val imageBitmap = data?.extras?.get("data") as Bitmap

//            val imageFile = File(requireActivity().externalCacheDir, "captured_image.jpg")
//            val imageBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
////
////            // Do something with the captured image bitmap
//            captured_image.setImageBitmap(imageBitmap)
//            captured_image.visibility = View.VISIBLE

            val mutableBitmap = imageBitmap.copy(Bitmap.Config.ARGB_8888, true)
            var referenceQuality = calculateImageQualityScore(mutableBitmap)
            Toast.makeText(context, "reference quality score $referenceQuality", Toast.LENGTH_SHORT).show()

            if(referenceQuality > 4.25)
                singleImageFaceDetection(mutableBitmap)
            else {
                Toast.makeText(context, "reference quality score low", Toast.LENGTH_SHORT).show()
                image_batch_check_button.isEnabled = false
            }
//            check_boxes.visibility = View.VISIBLE
//            image_batch_check_button.isEnabled = true
//            image_batch_check_button.setOnClickListener { checkAllImagesQuality(selectedImages) }
        }

        //For multiple images on the same page

        if (requestCode == REQUEST_CODE_PICK_IMAGES && resultCode == Activity.RESULT_OK) {

            // Check if the selected images fall within the desired range
            if (data?.clipData?.itemCount in 1 until 16) {

                val imageUris = data?.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)

                // Retrieve the selected images
//                if (data?.clipData != null) {
//                    for (i in 0 until data.clipData!!.itemCount) {
//                        selectedImages.add(data.clipData!!.getItemAt(i).uri)
//                    }
//                } else if (data?.data != null) {
//                    selectedImages.add(data.data!!)
//                }

                // Do something with the selected images
                // ...

                if (data?.clipData != null) {
                    for (i in 0 until data.clipData!!.itemCount) {
                        selectedImages.add(data.clipData!!.getItemAt(i).uri)
                    }
                } else if (data?.data != null) {
                    selectedImages.add(data.data!!)
                }


//                grid_recycler_view.adapter?.notifyDataSetChanged()

                for(image in selectedImages)
                    addGridItem(image, "Quality Score -", "Faces - ", "Similarity -")

                if(imageCapture == null) {
                    start_camera_button.visibility = View.VISIBLE
                    start_camera_button.setOnClickListener {
                        view_finder.visibility = View.VISIBLE
                        start_camera_button.visibility = View.GONE
                        capture_image_button.visibility = View.VISIBLE
                        view_finder.post {
                            beginPreview()
                        }
//                        if (ContextCompat.checkSelfPermission(
//                                requireContext(),
//                                Manifest.permission.CAMERA
//                            )
//                            == PackageManager.PERMISSION_GRANTED
//                        ) {
//                            view_finder.post {
//                                beginPreview()
//                            }
//                        } else {
//                            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_PERMISSIONS_CAMERA)
//                        }
                    }
                }
                else
                    image_batch_check_button.isEnabled = true


                check_boxes.visibility = View.VISIBLE
//                image_batch_check_button.isEnabled = true
                score_box.visibility = View.VISIBLE
                image_batch_check_button.setOnClickListener { checkAllImagesQuality(selectedImages) }

//                process_images_button.setOnClickListener { processImages(selectedImages) }
            } else {
                Toast.makeText(context, "Please select 2 to 15 images", Toast.LENGTH_SHORT).show()
            }
        }

        //For a single image - Without Glide

//        if (requestCode == REQUEST_IMAGE_PICK && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
//            val imageUri = data.data
//
//            val input: InputStream? = imageUri?.let { context?.contentResolver?.openInputStream(it) }
//
////            val exif = input?.let { ExifInterface(it) }
////            val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
////
////            val options = BitmapFactory.Options()
////            options.inSampleSize = 1
////            val bitmap = BitmapFactory.decodeStream(input, null, options)
////
////            val matrix = Matrix()
////            when (orientation) {
////                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
////                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
////                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
////            }
//
////
////            val exif = ExifInterface(input!!)
////            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
////            var rotationDegrees = 0
////            when (orientation) {
////                ExifInterface.ORIENTATION_ROTATE_90 -> rotationDegrees = 90
////                ExifInterface.ORIENTATION_ROTATE_180 -> rotationDegrees = 180
////                ExifInterface.ORIENTATION_ROTATE_270 -> rotationDegrees = 270
////            }
//
////             Then, decode the input stream into a bitmap
//            val bitmap = BitmapFactory.decodeStream(input)
//            input?.close()
////            val rotatedBitmap = bitmap?.let { Bitmap.createBitmap(it, 0, 0, bitmap.width, bitmap.height, matrix, true) }
//            val resizedBitmap = bitmap?.let { Bitmap.createScaledBitmap(it, 512, 512, false) }
////            val mutableBitmap = resizedBitmap?.copy(Bitmap.Config.ARGB_8888, true)
//
//            gallery_image.setImageBitmap(resizedBitmap)
//            gallery_image.visibility = View.VISIBLE
//
//            check_boxes.visibility = View.VISIBLE
//            quality_check_button.isEnabled = true
//            quality_check_button.setOnClickListener {
//                val mutableBitmap = resizedBitmap?.copy(Bitmap.Config.ARGB_8888, true)
//                if(mutableBitmap?.let { it1 -> calculateSharpnessFunc(it1) }!! > 2.75) {
//
//                    Toast.makeText(context, "Image Quality is Fine. Move to face detection.", Toast.LENGTH_SHORT).show()
//
//                    quality_check_button.isEnabled = false
//                    face_detection_button.isEnabled = true
//
//                    face_detection_button.setOnClickListener { singleImageFaceDetection(mutableBitmap) }
//                } else {
//                    Toast.makeText(context, "Poor Image Quality. Re-upload", Toast.LENGTH_SHORT).show()
//                }
//            }

//            Glide.with(requireContext())
//                .asBitmap()
//                .load(imageUri)
//                .into(object : CustomTarget<Bitmap>() {
//                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
//                        // Do something with the Bitmap object here
//                        // For example, you can set it to an ImageView
//                        val resizedBitmap = Bitmap.createScaledBitmap(resource, 512, 512, false)
//                        gallery_image.setImageBitmap(resizedBitmap)
//                        gallery_image.visibility = View.VISIBLE
//
//                        check_boxes.visibility = View.VISIBLE
//                        quality_check_button.isEnabled = true
//                        quality_check_button.setOnClickListener {
//                            val mutableBitmap = resizedBitmap.copy(Bitmap.Config.ARGB_8888, true)
//                            if(calculateSharpnessFunc(mutableBitmap) > 2.75) {
//
//                                Toast.makeText(context, "Image Quality is Fine. Move to face detection.", Toast.LENGTH_SHORT).show()
//
//                                quality_check_button.isEnabled = false
//                                face_detection_button.isEnabled = true
//
//                                face_detection_button.setOnClickListener { singleImageFaceDetection(mutableBitmap) }
//                            } else {
//                                Toast.makeText(context, "Poor Image Quality. Re-upload", Toast.LENGTH_SHORT).show()
//                            }
//                        }
//                    }
//                    override fun onLoadCleared(placeholder: Drawable?) {
//                        // Do nothing
//                    }
//                })
    }

    //Below function sets up CameraX preview and intializes Image Capture instance
    private fun beginPreview() {
        image_batch_check_button.isEnabled = false
        select_image.isEnabled = false

        val preview = Preview.Builder()
            .build()

        imageCapture = ImageCapture.Builder()
            .build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val camera = cameraProvider.bindToLifecycle(
                viewLifecycleOwner, cameraSelector, preview, imageCapture
            )

            preview.setSurfaceProvider(view_finder.surfaceProvider)

        }, ContextCompat.getMainExecutor(requireContext()))
    }


    //Grid Recycler view functions to modify and add property values like quality scoore, faces, and recognition score.

    private fun addGridItem(property1: Uri, property2: String?, property3: String?, property4: String?) {
        val newItem = GridItem(property1, property2, property3, property4)
        gridItems.add(newItem)
        grid_recycler_view.adapter?.notifyItemInserted(gridItems.size - 1)
    }

    private fun setGridItemPropertyQuality(position: Int, quality: String?) {
        var item = gridItems[position]
        Log.i("QUALITY SETTING", "quality position $position")
        item.qualityScoreText = quality
        grid_recycler_view.adapter?.notifyItemChanged(position) // Notify adapter of property1 change only
    }

    private fun setGridItemPropertyFaces(position: Int, faces: String?) {
        val item = gridItems[position]
        Log.i("FACES SETTING", "faces position $position")
        item.numberOfFacesText = faces
        grid_recycler_view.adapter?.notifyItemChanged(position) // Notify adapter of property1 change only
    }

    private fun setGridItemPropertySimilarity(position: Int, similarity: String?) {
        val item = gridItems[position]
        Log.i("SIMILARITY SETTING", "similarity position $position")
        item.similarityScoreText = similarity
        grid_recycler_view.adapter?.notifyItemChanged(position) // Notify adapter of property1 change only
    }


    //    NOT USED ANYMORE - (Time intensive) used for resizing image while maintaining aspect ratio and keeping size below a max size(here 1 MB)
    fun resizeBitmapAspect(bitmap: Bitmap, maxSize: Int): Bitmap {
        var width = bitmap.width
        var height = bitmap.height
        val aspectRatio = width.toFloat() / height.toFloat()
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        var bitmapData = byteArrayOutputStream.toByteArray()
        BitmapFactory.decodeByteArray(bitmapData, 0, bitmapData.size, options)
        var inSampleSize = 1
        while (bitmapData.size / 1024 > maxSize) {
            inSampleSize *= 2
            options.inSampleSize = inSampleSize
            bitmapData = byteArrayOutputStream.toByteArray()
            BitmapFactory.decodeByteArray(bitmapData, 0, bitmapData.size, options)
        }
        width = options.outWidth
        height = options.outHeight
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
        return scaledBitmap
    }

    //    NOT USED ANYMORE - used for resizing with compression
    fun resizeImage(image: Bitmap, maxSize: Int): Bitmap {
        val maxFileSize = maxSize * 1024 * 1024 // Convert maxSize from MB to bytes
        var compression: Float = 1.0f
        var resizedImage = image
// Reduce image size until it's smaller than the maximum size
        while (resizedImage.byteCount > maxFileSize) {
            val outputStream = ByteArrayOutputStream()
            resizedImage.compress(Bitmap.CompressFormat.JPEG, (compression * 100).toInt(), outputStream)
            val imageData = outputStream.toByteArray()
            if (imageData.size > maxFileSize) {
                val ratio = sqrt(maxFileSize.toDouble() / imageData.size) // Compute the compression ratio to apply
                val newWidth = (resizedImage.width * ratio).toInt()
                val newHeight = (resizedImage.height * ratio).toInt()
                val newSize = Size(newWidth, newHeight)

                val bitmap = Bitmap.createBitmap(newSize.width, newSize.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                canvas.drawBitmap(resizedImage, null, Rect(0, 0, newSize.width, newSize.height), paint)

                resizedImage.recycle()
                resizedImage = bitmap
            }
            compression *= 0.9f // Reduce compression quality for next iteration
        }

        return resizedImage
    }

    private fun loadBitmapsFromUris(context: Context, imageUris: List<Uri>, callback: (MutableList<Bitmap>) -> Unit) {
        //creating a default bitmap that will be used as an initialization object for bitmaps mutable list
        val defaultBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) // The default value for the list
        val bitmaps= MutableList<Bitmap>(imageUris.size) {defaultBitmap}
        val positions = mutableListOf<Int>()
        var count = 0

        //Storing indices in positions mutable list
        for (i in imageUris.indices) {
            positions.add(i)
        }

        //We iterate over positions mutable to keep track of which image uri does Glide library return bitmap of.
        //Since Glide library run asynchronously, we need to keep the order of bitmaps being returned same as order of image uris
        //Otherwise the details of each image appear jumbled in Grid Recycler view.

        for (i in positions) {
            val uri = imageUris[i]
            Glide.with(context)
                .asBitmap()
                .load(uri)
                .into(object : SimpleTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        var resizedBitmap = resource
//                        if(resource.width > 512 && resource.height > 512)
//                            resizedBitmap = Bitmap.createScaledBitmap(resource, 512, 512, false)


                        // Get the number of bytes required to store the bitmap
//                        val byteCount = resizedBitmap.byteCount
//
//                        // Convert bytes to MB
//                        val megabytes = byteCount / (1024f * 1024f)
//
//                        if(megabytes > 1)
//                            resizedBitmap = resizeBitmapAspect(resizedBitmap, 1000000)

                        //Below resizing maintains aspect ratio while keeping max dimensions 512 X 512
                        if(resource.width > 512 && resource.height > 512)
                            resizedBitmap = resizeBitmap(resource, 512,512)

                        val mutableBitmap = resizedBitmap.copy(Bitmap.Config.ARGB_8888, true)
//                        bitmaps.add(mutableBitmap)

                        bitmaps[i] = mutableBitmap
                        count++
                        if (count == imageUris.size) {
                            callback(bitmaps)
                        }
                    }
                })
        }
    }

    //All images' quality is checked via this function
    private fun checkAllImagesQuality(images: MutableList<Uri>) {
        var countCorrectImages = 0
        var wrongImages: ArrayList<Int> = ArrayList()
        var bitmaps: MutableList<Bitmap> = mutableListOf()
        val qualityScores = ArrayList<Double>()

        val progressDialog = ProgressDialog(context)
        progressDialog.setMessage("Checking Quality...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        //Loading Images using Glide to avoid rotation of image
        loadBitmapsFromUris(requireContext(), images) { bitmaps ->
            // Use the loaded bitmaps here
            var i = 0

            bitmaps.forEach {
                val qualityScore = calculateImageQualityScore(it)

                Log.i("QUALITY SCORE", "$qualityScore ${i + 1}")

                qualityScores.add(qualityScore)
                val qualityTwoDecimals = String.format("%.2f", qualityScore)
                setGridItemPropertyQuality(i, "Quality Score - $qualityTwoDecimals")

                if(qualityScore > 2.6)
                    countCorrectImages++
                else
                    wrongImages.add(i + 1)

                i++
            }

            progressDialog.dismiss()

            if(wrongImages.size == 0) {
                checkMap["Quality"] = true

                image_quality_text.text = "Image Quality Check - All Passed "
//                detectFacesInImages(bitmaps, images)
            }
            else {
                image_quality_text.text = "Image Quality Check - Failed in images ${wrongImages}. Re-upload."
            }

            detectFacesInImages(bitmaps, images)
        }
    }

    //Coroutine is launched here
    fun detectFacesInImages(bitmaps: MutableList<Bitmap>, images: List<Uri>) {
//        var nonOneFaceImages: ArrayList<Int> = ArrayList()
        faceCount = MutableList<Int>(images.size) {0}
        nonOneFaceImages.clear()
        val progressDialog = ProgressDialog(context)
        progressDialog.setMessage("Detecting Faces...")
        progressDialog.setCancelable(false)
        progressDialog.show()
        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            val detectedFaces = processImagesHelper(bitmaps, images)
            progressDialog.dismiss()
            val endTime = System.currentTimeMillis()
            Log.i("FACE DETECTION TIME", "${((endTime - startTime).toFloat()/1000.0).toFloat()} seconds")

            for (i in images.indices) {
                setGridItemPropertyFaces(i, "Faces - ${faceCount[i]}")
            }

            if (nonOneFaceImages.isEmpty()) {
//                Toast.makeText(context, "All images have exactly one face", Toast.LENGTH_SHORT).show()
                checkMap["Detection"] = true

                face_detection_text.text = "Face Detection Check - Passed Time Taken ${((endTime - startTime).toFloat()/1000.0).toFloat()}"
//                faceRecognitionForAllImages(bitmaps, detectedFaces)

//                tempfaceRecog(bitmaps, detectedFaces)

            } else {
                face_detection_text.text = "Face Detection Check - Failed in images ${nonOneFaceImages}. Time Taken ${((endTime - startTime).toFloat()/1000.0).toFloat()}"
//                Toast.makeText(context, "Images ${nonOneFaceImages} do not have exactly one face", Toast.LENGTH_SHORT).show()
            }

            faceRecognitionForAllImages(bitmaps, detectedFaces, nonOneFaceImages)
        }
    }

    //For loop runs here
    suspend fun processImagesHelper(bitmaps: MutableList<Bitmap>, images: List<Uri>): MutableList<Face> = withContext(Dispatchers.IO) {
        val detectedFaces = mutableListOf<Face>()
//        val detectedFaces = mutableListOf<Bitmap>()
        var i = 0

        //Below Code iterates over bitmaps list
        bitmaps.forEach { bitmap ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            Log.i("IMAGE SIZE", "HEIGHT: ${inputImage?.height} WIDTH :${inputImage?.width}")

            val faces = detectFacesHelper(inputImage, bitmap)

            faceCount[i] = faces.size
//            setGridItemPropertyFaces(i, "Faces - ${faces.size}")


            if (faces.size == 1) {
                detectedFaces.addAll(faces)
            }
            else{
                nonOneFaceImages.add(i + 1)
            }
            i++
        }

        //Below Code iterates over uri list
//        images.forEach { uri ->
//
//            //Method 1 - URI to input stream to bitmap to resized bitmap to mutable bitmap to inputImage
//            val input: InputStream? = context?.contentResolver?.openInputStream(uri)
//            val bitmap = BitmapFactory.decodeStream(input)
//            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 512, 512, true)
//            val mutableBitmap = resizedBitmap.copy(Bitmap.Config.ARGB_8888, true)
//            val inputImage = InputImage.fromBitmap(mutableBitmap, 0)
//
//
//
//            //Method 2 - URI to Input image to
////            val image = InputImage.fromFilePath(requireContext(), uri)
////            val image2 = resize(image, 512, 512)
////            val bitmap = image.bitmapInternal
////            val resizedBitmap = resizeBitmap(bitmap, 640, 480)
//
//
//            Log.i("IMAGE SIZE", "HEIGHT: ${inputImage?.height} WIDTH :${inputImage?.width}")
//
//
//            val faces =  detectFacesHelper(inputImage, mutableBitmap)
//            if (faces?.size == 1) {
//                detectedFaces.addAll(faces)
//            }
//            else {
//                nonOneFaceImages.add(i + 1)
//            }
//            i++
//        }
        detectedFaces
    }

    //Face detection process occurs here on each image(async task)
    private suspend fun detectFacesHelper(image: InputImage, mutableBitmap: Bitmap): MutableList<Face> = withContext(Dispatchers.IO) {
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build())

//        detector.process(InputImage.fromBitmap(mutableBitmap, 0))
//            .addOnSuccessListener {faces ->
//                if(faces.size == 1)
//                    faceList.addAll(faces)
//
//            }
//            .addOnFailureListener {
//                Log.i("ERR", "error : $it")
//            }
        val resultTask = detector.process(image)
        val emptyList = mutableListOf<Face>()


        Tasks.await(resultTask)
        resultTask?.let { it ->
            val faces = it.result.toMutableList()

            return@withContext faces

            //Below code used to only return faces mutable list if its size was 1 (i.e. 1 face is detected)
            //Now we wish to return all faces mutable lists since we want to continue running image processing
            // even if some image does not contain just a single face

//            if (faces.size == 1) {
//                Log.i("SINGLE FACE", "single face in this image")
////                val boundingBox = faces[0].boundingBox
////                val left = boundingBox.left.toInt()
////                val top = boundingBox.top.toInt()
////                val right = boundingBox.right.toInt()
////                val bottom = boundingBox.bottom.toInt()
////                val faceBitmap = Bitmap.createBitmap(mutableBitmap, left, top, right - left, bottom - top)
//////                val faceBitmap = Bitmap.createBitmap(mutableBitmap, left, top, right, bottom)
////
////                faceBitmaps.add(faceBitmap)
////                return@withContext faceBitmaps
//                return@withContext faces
//            }
//            else if(faces.size == 0){
//                Log.i("NO FACE", "No face in this image")
//                return@withContext emptyList
//            }
//            else {
//                Log.i("MULTIPLE FACE", "multiple faces in this image")
//                return@withContext emptyList
//            }
        }
        emptyList
//        faceBitmaps
    }

    //Face recognition function with codes for both Facenet and Mobile Facenet models
    private fun faceRecognitionForAllImages(bitmaps: MutableList<Bitmap>, faceList: List<Face>, nonOneFaces: MutableList<Int>) {
        //Perform face recognition on all the faces in the array


        //Mobile Face Net Model Code

        //NOT USED ANYMORE -Below code was used when we took the first image of batch as reference face


//        val firstFace = faceList.first()
//
//        val boundingBox = firstFace.boundingBox
//        val left = max(0, boundingBox.left.toInt())
//        val top = max(0, boundingBox.top.toInt())
//        val right = min(bitmaps[0].width, boundingBox.right.toInt())
//        val bottom = min(bitmaps[0].height, boundingBox.bottom.toInt())
//        val firstFaceBitmap = Bitmap.createBitmap(bitmaps[0], left, top, right - left, bottom - top)
//
        val startTime = System.currentTimeMillis()
//        val firstFaceEmbedding = getFaceEmbedding(firstFaceBitmap, tflite)


        var facesNotMatch: MutableList<Int> = mutableListOf()
        // Compare embeddings of all faces with the first face embedding

        var j = 0
        var allFacesMatch = true
        val progressDialog = ProgressDialog(context)
        progressDialog.setMessage("Finding Similarity...")
        progressDialog.setCancelable(false)
        progressDialog.show()
        for (i in 0 until bitmaps.size) {

            if ((i + 1) !in nonOneFaces) {

                val face = faceList[j]
//            val resizedCurrentBitmap = resizeBitmap(bitmaps[i], 640, 480)
//            val bitmapThisFace = Bitmap.createBitmap(
//                bitmaps[i],
//                face.boundingBox.left,
//                face.boundingBox.top,
//                face.boundingBox.width(),
//                face.boundingBox.height()
//            )


                //Below code ensures that face cropping coordinates do not go out of bound
                val boundingBox = face.boundingBox
                val left = max(0, boundingBox.left.toInt())
                val top = max(0, boundingBox.top.toInt())
                val right = min(bitmaps[i].width, boundingBox.right.toInt())
                val bottom = min(bitmaps[i].height, boundingBox.bottom.toInt())
                val currentFaceBitmap = Bitmap.createBitmap(bitmaps[i], left, top, right - left, bottom - top)

//                val leftEye = face.getLandmark(MOUTH_LEFT)?.position
//                val rightEye = face.getLandmark(MOUTH_RIGHT)?.position
//                val noseBase = face.getLandmark(MOUTH_BOTTOM)?.position
//
//                val dx = rightEye?.x?.minus(leftEye?.x ?: 0F) ?: 0F
//                val dy = rightEye?.y?.minus(leftEye?.y ?: 0F) ?: 0F
//
//                val angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
//
//                val mouthLeft = face.getLandmark(MOUTH_LEFT)?.position
//                val mouthRight = face.getLandmark(MOUTH_RIGHT)?.position
//                val nose = face.getLandmark(NOSE_BASE)?.position
//                val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)?.position
//                val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)?.position
//
//                val x = mouthLeft?.x ?: 0F
//                val y = nose?.y ?: 0F
//                val width = (mouthRight?.x ?: 0F) - x
//                val height = (leftCheek?.y ?: rightCheek?.y ?: 0F) - y
//
//                val matrix = Matrix()
//                matrix.postRotate(angle, x + width / 2, y + height / 2)
//                matrix.postTranslate(-x, -y)
//
//                val alignedBitmap = Bitmap.createBitmap(
//                    currentFaceBitmap, 0, 0, currentFaceBitmap.width, currentFaceBitmap.height, matrix, true
//                )

                val currentFaceEmbedding = getFaceEmbedding(currentFaceBitmap, tflite)


//                val currentFaceEmbedding = getFaceEmbedding(alignedBitmap, tflite)

                Log.i("EMBEDDINGS", "Face Embedding ${currentFaceEmbedding}")

//            if (!isSimilar(firstFaceEmbedding, currentFaceEmbedding)) {
//                facesNotMatch.add(i + 1)
//                allFacesMatch = false
//            }


//            var cosineSimilarity: Float = 0.0F
//            if(referenceFaceEmbedding != null)
//                cosineSimilarity = calculateCosineSimilarity(firstFaceEmbedding, currentFaceEmbedding)
//            else
//                Toast.makeText(context, "NO REFERENCE FACE", Toast.LENGTH_SHORT).show()

                //euclidean distance
                var distance = euclideanDistance(referenceFaceEmbedding, currentFaceEmbedding)
                val distanceTwoDecimals = String.format("%.2f", distance)
                setGridItemPropertySimilarity(i, "Distance - $distanceTwoDecimals")


                if(distance > 1.10) {
                    facesNotMatch.add(i + 1)
                    allFacesMatch = false
                }


                //cosine similarity
//                var cosineSimilarity = calculateCosineSimilarity(referenceFaceEmbedding, currentFaceEmbedding)
//                val similarityTwoDecimals = String.format("%.2f", cosineSimilarity)
//                setGridItemPropertySimilarity(i, "Similarity - $similarityTwoDecimals")
//
//                if(cosineSimilarity < 0.65) {
//                    facesNotMatch.add(i + 1)
//                    allFacesMatch = false
//                }

                j++
            }
        }

        progressDialog.dismiss()



        val endTime = System.currentTimeMillis()
//        Log.i("FACE RECOGNITION TIME", "${((endTime - startTime).toFloat()/1000.0).toFloat()} seconds")

        if(allFacesMatch && faceList.isNotEmpty()) {
            checkMap["Recognition"] = true
            face_recognition_text.text = "Face Recognition Check - Passed Time Taken ${((endTime - startTime).toFloat()/1000.0).toFloat()}"
            Toast.makeText(requireContext(), "Image Batch Passed ", Toast.LENGTH_SHORT).show()
        }
        else if(faceList.isEmpty()) {
            face_recognition_text.text = "Recognition Failed. No or multiple faces in each image. Time taken ${((endTime - startTime).toFloat()/1000.0).toFloat()} "
        }
        else {
            face_recognition_text.text = "Face Recognition Check - Failed in images ${nonOneFaces} & ${facesNotMatch}  . Time taken ${((endTime - startTime).toFloat()/1000.0).toFloat()} "
        }

        // Show the result
//        val resultText = if (allFacesMatch) "All faces match the first face."
//        else "Faces ${facesNotMatch} not match the first face."
//        Toast.makeText(requireContext(), resultText, Toast.LENGTH_SHORT).show()

//        Face Net Model Code

//        val firstFace = faceList.first()
//        val firstFaceBitmap = Bitmap.createBitmap(
//            bitmaps[0],
//            firstFace.boundingBox.left,
//            firstFace.boundingBox.top,
//            firstFace.boundingBox.width(),
//            firstFace.boundingBox.height()
//        )
//
//
//        val firstFaceEmbedding = getFaceEmbedding(firstFaceBitmap, interpreter)
//        var facesNotMatch: ArrayList<Int> = ArrayList()
//        // Loop through remaining faces in list and check for similarity
//        var allSimilar = true
//        for (i in 1 until faceList.size) {
//            val face = faceList[i]
//
//            val bitmapThisFace = Bitmap.createBitmap(
//                bitmaps[i],
//                face.boundingBox.left,
//                face.boundingBox.top,
//                face.boundingBox.width(),
//                face.boundingBox.height()
//            )
//
//            val currentFaceEmbedding = getFaceEmbedding(bitmapThisFace, interpreter)
//
//            if(!isSimilar(firstFaceEmbedding, currentFaceEmbedding)) {
//                facesNotMatch.add(i + 1)
//                allSimilar = false
//            }
//        }
//            val resultText = if (allSimilar) "All faces match the first face."
//            else "Faces ${facesNotMatch.toString()} not match the first face."
//            Toast.makeText(requireContext(), resultText, Toast.LENGTH_SHORT).show()


//      Histogram comparison approach for face recognition
//        Utils.bitmapToMat(bitmap, grayImage)
//        Imgproc.cvtColor(grayImage, grayImage, Imgproc.COLOR_RGBA2GRAY)
//
//
////        val recognizerSF: FaceRecognizerSF = FaceRecognizerSF.create()
//
//        // Loop through the rest of the faces and compare them to the first face
//        var allSamePerson = true
//        for (i in 1 until faceList.size) {
//            val face = faceList[i]
//            val faceImage = Mat()
//
//            val bitmapThisFace = Bitmap.createBitmap(
//                bitmaps[i],
//                face.boundingBox.left,
//                face.boundingBox.top,
//                face.boundingBox.width(),
//                face.boundingBox.height()
//            )
//            Utils.bitmapToMat(bitmapThisFace, faceImage)
//            Imgproc.cvtColor(faceImage, faceImage, Imgproc.COLOR_RGBA2GRAY)
//            val similarity = compareImages(grayImage, faceImage)
//            Log.i("SIMILARITY", similarity.toString())
//            if (similarity < 0.7) { // Set a threshold for similarity
//                allSamePerson = false
//                break
//            }
//        }
//
//        // Determine whether all faces belong to the same person
//        if (allSamePerson) {
//            // All faces belong to the same person
//            Toast.makeText(context, "All Faces of same person", Toast.LENGTH_SHORT).show()
//        } else {
//            // Not all faces belong to the same person
//            Toast.makeText(context, "Faces do not match", Toast.LENGTH_SHORT).show()
//        }

    }

    //NOT USED ANYMORE -Temporary Face recognition function with codes for both Facenet and Mobile Facenet models
    private fun tempfaceRecog(bitmaps: MutableList<Bitmap>, faceList: MutableList<Bitmap>) {
        //Perform face recognition on all the faces in the array

        val firstFace = faceList.first()


        val startTime = System.currentTimeMillis()
        val firstFaceEmbedding = getFaceEmbedding(firstFace, tflite)


        var facesNotMatch: ArrayList<Int> = ArrayList()
        // Compare embeddings of all faces with the first face embedding

        var allFacesMatch = true
        for (i in 1 until faceList.size) {
            val face = faceList[i]
            val currentFaceEmbedding = getFaceEmbedding(face, tflite)
            Log.i("EMBEDDINGS", "First Image embeddings : ${firstFaceEmbedding} and Second Image embedding: ${currentFaceEmbedding}")

//            if (!isSimilar(firstFaceEmbedding, currentFaceEmbedding)) {
//                facesNotMatch.add(i + 1)
//                allFacesMatch = false
//            }

            if(calculateCosineSimilarity(firstFaceEmbedding, currentFaceEmbedding) < 0.6) {
                facesNotMatch.add(i + 1)
                allFacesMatch = false
            }
        }


        val endTime = System.currentTimeMillis()
//        Log.i("FACE RECOGNITION TIME", "${((endTime - startTime).toFloat()/1000.0).toFloat()} seconds")

        if(allFacesMatch) {
            checkMap["Recognition"] = true
            face_recognition_text.text = "Face Recognition Check - Passed Time Taken ${((endTime - startTime).toFloat()/1000.0).toFloat()}"
            Toast.makeText(requireContext(), "Image Batch Passed ", Toast.LENGTH_SHORT).show()
        }
        else {
            face_recognition_text.text = "Face Recognition Check - Failed in images $facesNotMatch. Time taken ${((endTime - startTime).toFloat()/1000.0).toFloat()} "
        }

    }

    //NOT USED ANYMORE - Previously used for Face Detection - Async operations issue
    private fun detectFacesForAllImages(bitmaps: MutableList<Bitmap>) {
        //Run Face Detection and check whether each image has a single face
        // If yes then store the faces in an array, if not then discard
        var facesArray: MutableList<Face> = mutableListOf()
        var imagesProcessed = 0
        var wrongImages: ArrayList<Int> = ArrayList()
        GlobalScope.launch(Dispatchers.Main) {
            for (i in 0 until bitmaps.size) {
                val faces = detectFaces(bitmaps[i])
                Log.i("FACE ARRAY", faces.toString())
                if (faces.size != 1) {
//                Toast.makeText(context, "More or less than 1 face detected. Can't proceed. ", Toast.LENGTH_SHORT).show()
                    Log.i(
                        "NUMBER OF FACES",
                        faces.size.toString() + "In less than or more than 1 face "
                    )
                    wrongImages.add(i + 1)
                } else {
                    Log.i("NUMBER OF FACES", faces.size.toString() + "In equal to 1 face ")
                    imagesProcessed++
                    facesArray.add(faces[0])
                }
            }


            if (wrongImages.size == 0) {
                Toast.makeText(
                    context,
                    "All images pass face detection check. Proceed to face recognition",
                    Toast.LENGTH_SHORT
                ).show()
                checkMap["Detection"] = true
//                quality_check_button.isEnabled = false
//                face_detection_button.isEnabled = false
//                face_recognition_button.isEnabled = true

//                face_recognition_button.setOnClickListener{ faceRecognitionForAllImages(bitmaps, facesArray) }
            } else {
                Log.i(
                    "NUMBER OF FACES",
                    facesArray.size.toString() + "and size of bitmaps" + bitmaps.size.toString()
                )
                Toast.makeText(
                    context,
                    "Image[s] ${wrongImages.toString()} do not pass face detection check",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    //NOT USED RIGHT NOW - for resizing inputImage while maintaining aspect ratio
    fun resize(inputImage: InputImage, maxWidth: Int, maxHeight: Int): InputImage? {
        val width = inputImage.width
        val height = inputImage.height

        // Determine the scale factor that preserves the aspect ratio
        val scaleFactor = when {
            width > height -> maxWidth.toFloat() / width.toFloat()
            else -> maxHeight.toFloat() / height.toFloat()
        }

        // Compute the new dimensions
        val newWidth = (width * scaleFactor).toInt()
        val newHeight = (height * scaleFactor).toInt()

        // Create a Bitmap from the InputImage
        val bitmap = inputImage.bitmapInternal

        // Resize the Bitmap using bicubic interpolation
        val resizedBitmap = bitmap?.let { Bitmap.createScaledBitmap(it, newWidth, newHeight, true) }

        // Create a new InputImage from the resized Bitmap
        return resizedBitmap?.let { InputImage.fromBitmap(it, inputImage.rotationDegrees)}
    }

    //USED CURRENTLY - for resizing bitmap while maintaining aspect ratio and keeping within limits of maxHeight and maxWidth
    fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Determine the scale factor that preserves the aspect ratio
        val scaleFactor = when {
            width > height -> maxWidth.toFloat() / width.toFloat()
            else -> maxHeight.toFloat() / height.toFloat()
        }

        // Compute the new dimensions
        val newWidth = (width * scaleFactor).toInt()
        val newHeight = (height * scaleFactor).toInt()

        // Resize the Bitmap using bicubic interpolation
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

        return resizedBitmap
    }


    //Image Quality Score Function - Only return sharpness now
    private fun calculateImageQualityScore(bitmap: Bitmap): Double {

//        Resolution

//        val width: Int = bitmap.width
//        val height: Int = bitmap.height
//        val resolution: Int = width * height
//        // Use the resolution as a factor
//        val resolutionFactor: Double = resolution.toDouble() / (1000 * 1000) // divide by 1 million for scaling

//        Calculate brightness and contrast using mean and standard deviation of pixel intensities

//        val pixels = IntArray(bitmap.width * bitmap.height)
//        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
//        var sum = 0L
//        for (pixel in pixels) {
//            sum += Color.red(pixel)
//        }
//        val mean = sum / (bitmap.width * bitmap.height)
//        var sumSquares = 0L
//        for (pixel in pixels) {
//            val diff = Color.red(pixel) - mean
//            sumSquares += diff * diff
//        }
//        val stdDev = sqrt(sumSquares / (bitmap.width * bitmap.height - 1).toFloat())
//        val brightness = mean / 255f
//        val contrast = stdDev / 128f
//
//        OpenCVLoader.initDebug()
//
//        val grayScaleBitmap = toGrayscale(bitmap)
//        val laplacianBitmap = applyLaplacian(grayScaleBitmap)
//
//        // Calculate variance of the laplacianBitmap
//        val variance = calculateVariance(laplacianBitmap)
//
//        // Normalize variance to a score between 0 and 1
//        val score = (variance / 10000).coerceIn(0.0, 1.0)
//
//        OpenCVLoader.initDebug(false)

//        NOT USED ANYMORE - Calculate sharpness using variance of Laplacian

//        val laplacian = floatArrayOf(
//            0f, -1f, 0f,
//            -1f, 4f, -1f,
//            0f, -1f, 0f
//        )
//        val lapBitmap = Bitmap.createBitmap(bitmap)
//        val lapCanvas = Canvas(lapBitmap)
//        val lapPaint = Paint()
//        lapPaint.colorFilter =
//            android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(laplacian))
//        lapCanvas.drawBitmap(lapBitmap, 0f, 0f, lapPaint)
//        val lapPixels = IntArray(lapBitmap.width * lapBitmap.height)
//        lapBitmap.getPixels(lapPixels, 0, lapBitmap.width, 0, 0, lapBitmap.width, lapBitmap.height)
//        var sumSquaresLap = 0L
//        for (pixel in lapPixels) {
//            val intensity = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3f
//            sumSquaresLap += (intensity * intensity).toLong()
//        }
//        val sharpness = sqrt(sumSquaresLap / (lapBitmap.width * lapBitmap.height - 1).toFloat())

//        val sharpness = score


        //Here we calculate sharpness using custom functions
        val sharpness = calculateSharpnessFunc(bitmap)

//        Calculate color balance using mean and standard deviation of color channels

//        var sumRed = 0L
//        var sumGreen = 0L
//        var sumBlue = 0L
//        for (pixel in pixels) {
//            sumRed += Color.red(pixel)
//            sumGreen += Color.green(pixel)
//            sumBlue += Color.blue(pixel)
//        }
//        val meanRed = sumRed / (bitmap.width * bitmap.height)
//        val meanGreen = sumGreen / (bitmap.width * bitmap.height)
//        val meanBlue = sumBlue / (bitmap.width * bitmap.height)
//        var sumSquaresRed = 0L
//        var sumSquaresGreen = 0L
//        var sumSquaresBlue = 0L
//        for (pixel in pixels) {
//            val diffRed = Color.red(pixel) - meanRed
//            val diffGreen = Color.green(pixel) - meanGreen
//            val diffBlue = Color.blue(pixel) - meanBlue
//            sumSquaresRed += diffRed * diffRed
//            sumSquaresGreen += diffGreen * diffGreen
//            sumSquaresBlue += diffBlue * diffBlue
//        }
//        val stdDevRed = sqrt(sumSquaresRed / (bitmap.width * bitmap.height - 1).toFloat())
//        val stdDevGreen = sqrt(sumSquaresGreen / (bitmap.width * bitmap.height - 1).toFloat())
//        val stdDevBlue = sqrt(sumSquaresBlue / (bitmap.width * bitmap.height - 1).toFloat())
//        val colorBalance =
//            1 - (sqrt(stdDevRed * stdDevRed + stdDevGreen * stdDevGreen + stdDevBlue * stdDevBlue) / sqrt(
//                3f * 255f * 255f
//            ))

//        Combine quality factors using weights
//        return brightness * BRIGHTNESS_WEIGHT + sharpness * SHARPNESS_WEIGHT + contrast * CONTRAST_WEIGHT + colorBalance * COLOR_BALANCE_WEIGHT

        return sharpness
    }

    //NOT USED ANYMORE - Helper functions for Image Quality Score function
    fun toGrayscale(bitmap: Bitmap): Bitmap {
        val grayBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565)
        val canvas = Canvas(grayBitmap)
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        val colorFilter = ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = colorFilter
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return grayBitmap
    }

    // Apply Laplacian operator to grayscale image
//    fun applyLaplacian(bitmap: Bitmap): Bitmap {
//        val laplacian = Mat()
//        Utils.bitmapToMat(bitmap, laplacian)
//        Imgproc.cvtColor(laplacian, laplacian, Imgproc.COLOR_BGR2GRAY)
//        Imgproc.Laplacian(laplacian, laplacian, CvType.CV_16S, 3)
//        val absLaplacian = Mat()
//        Core.convertScaleAbs(laplacian, absLaplacian)
//        Utils.matToBitmap(absLaplacian, bitmap)
//        return bitmap
//    }

    // Calculate variance of bitmap
//    fun calculateVariance(bitmap: Bitmap): Double {
//        val mat = Mat()
//        Utils.bitmapToMat(bitmap, mat)
//        val mean = MatOfDouble()
//        val stddev = MatOfDouble()
//        Core.meanStdDev(mat, mean, stddev)
//        val variance = stddev.get(0, 0)[0] * stddev.get(0, 0)[0]
//        return variance
//    }


    //USED CURRENTLY - Helper Function used to calculate Sharpness - USED IN IMAGE QUALITY FUNCTION
    private fun calculateSharpnessFunc(image: Bitmap): Double {
        val width = image.width
        val height = image.height
        val pixelCount = width * height

        val pixels = IntArray(pixelCount)
        image.getPixels(pixels, 0, width, 0, 0, width, height)

        var laplacianSum = 0.0

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x

                val topIndex = (y - 1) * width + x
                val topLeftIndex = (y - 1) * width + x - 1
                val topRightIndex = (y - 1) * width + x + 1

                val leftIndex = y * width + x - 1
                val rightIndex = y * width + x + 1

                val bottomIndex = (y + 1) * width + x
                val bottomLeftIndex = (y + 1) * width + x - 1
                val bottomRightIndex = (y + 1) * width + x + 1

                val laplacianValue = -pixels[topLeftIndex] - 2.0 * pixels[topIndex] - pixels[topRightIndex] +
                        pixels[bottomLeftIndex] + 2.0 * pixels[bottomIndex] + pixels[bottomRightIndex] +
                        pixels[leftIndex] + pixels[rightIndex] - 2.0 * pixels[index]

                laplacianSum += laplacianValue * laplacianValue
            }
        }

        val sharpness = sqrt(laplacianSum / pixelCount)
        return sharpness/1000000
    }

    //NOT USED ANYMORE - Viewpager adapter
    inner class ImagePagerAdapter(private val imageUris: List<Uri>) :
        RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.image_item, parent, false)
            return ImageViewHolder(view)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            holder.bind(imageUris[position])
        }

        override fun getItemCount(): Int {
            return imageUris.size
        }

        inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val imageView: ImageView = itemView.findViewById(R.id.image_view)
            private val scoreTextView: TextView = itemView.findViewById(R.id.score_text_view)
            private val detectFacesButton: Button = itemView.findViewById(R.id.detect_faces_button)
            private val numberOfFacesText: TextView = itemView.findViewById(R.id.number_of_faces_text)
            private val confidenceScoreText: TextView = itemView.findViewById(R.id.confidence_score_text)

            fun bind(uri: Uri) {
                Glide.with(itemView)
                    .load(uri)
                    .into(imageView)

                // Load the image into a Bitmap
                Glide.with(requireContext())
                    .asBitmap()
                    .load(uri)
                    .into(object : SimpleTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {

                            // Convert the Bitmap to a mutable Bitmap
                            val mutableBitmap = resource.copy(Bitmap.Config.ARGB_8888, true)


                            detectFacesButton.visibility = View.VISIBLE
//                            detectFacesButton.setOnClickListener { detectFaces(resource) }
                            // Do something with the mutable Bitmap
                            // ...
                            val qualityScore = calculateImageQualityScore(mutableBitmap)
                            val qualityScorePercentage = String.format("%.2f", qualityScore*100)
                            scoreTextView.text = "Quality Score: $qualityScorePercentage%"
                        }
                    })

            }
        }
    }

    //USED WITH CAMERA CAPTURED IMAGE - Face Detection function for processing a single image
    private fun singleImageFaceDetection(bitmap: Bitmap) {

//        val detector = FaceDetection.getClient(
//            FaceDetectorOptions.Builder()
//                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
//                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
//                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
//                .enableTracking()
//                .build()
//        )

        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build()
        )

        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { faces ->
                if(faces.size == 1) {
                    Toast.makeText(context, " Single face detected. Move to batch checking.", Toast.LENGTH_SHORT).show()
                    image_batch_check_button.isEnabled = true

                    val boundingBox = faces[0].boundingBox
                    val left = max(0, boundingBox.left.toInt())
                    val top = max(0, boundingBox.top.toInt())
                    val right = min(bitmap.width, boundingBox.right.toInt())
                    val bottom = min(bitmap.height, boundingBox.bottom.toInt())
                    val faceBitmap = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)

//                    val face = faces[0]
//                    val leftEye = face.getLandmark(MOUTH_LEFT)?.position
//                    val rightEye = face.getLandmark(MOUTH_RIGHT)?.position
//                    val noseBase = face.getLandmark(MOUTH_BOTTOM)?.position
//
//                    val dx = rightEye?.x?.minus(leftEye?.x ?: 0F) ?: 0F
//                    val dy = rightEye?.y?.minus(leftEye?.y ?: 0F) ?: 0F
//
//                    val angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
//
//                    val mouthLeft = face.getLandmark(MOUTH_LEFT)?.position
//                    val mouthRight = face.getLandmark(MOUTH_RIGHT)?.position
//                    val nose = face.getLandmark(NOSE_BASE)?.position
//                    val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)?.position
//                    val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)?.position
//
//                    val x = mouthLeft?.x ?: 0F
//                    val y = nose?.y ?: 0F
//                    val width = (mouthRight?.x ?: 0F) - x
//                    val height = (leftCheek?.y ?: rightCheek?.y ?: 0F) - y
//
//                    val matrix = Matrix()
//                    matrix.postRotate(angle, x + width / 2, y + height / 2)
//                    matrix.postTranslate(-x, -y)
//
//                    val alignedBitmap = Bitmap.createBitmap(
//                        faceBitmap, 0, 0, faceBitmap.width, faceBitmap.height, matrix, true
//                    )

                    referenceFaceEmbedding = getFaceEmbedding(faceBitmap, tflite)


//                    referenceFaceEmbedding = getFaceEmbedding(alignedBitmap, tflite)


//                    gallery_image.setImageBitmap(faceBitmap)
//                val faceBitmap = Bitmap.createBitmap(mutableBitmap, left, top, right, bottom)
//                    face_detection_button.isEnabled = false
//                    face_recognition_button.isEnabled = true
                }
                else {
//                    Toast.makeText(context, "Either no or multiple faces detected. Please re-capture.", Toast.LENGTH_SHORT).show()
                    val builder = AlertDialog.Builder(requireContext())
                    builder.setTitle("FACES Issue")
                    builder.setMessage("Either no or multiple faces detected. Please re-capture.")
                    builder.setPositiveButton("OK") { dialog, which ->
                        dialog.dismiss() // Close the dialog
                    }
                    builder.show()

                    image_batch_check_button.isEnabled = false
                }
            }
            .addOnFailureListener { e ->
                // Handle face detection failure
                Toast.makeText(context, "$e !", Toast.LENGTH_SHORT).show()
            }
    }

    //NOT USED ANYMORE - Previously used in Face Detection
    private suspend fun detectFaces(bitmap: Bitmap): MutableList<Face> = withContext(Dispatchers.IO){
        var facesList: MutableList<Face> = mutableListOf()
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking()
                .build()
        )

        val result = detector.process(InputImage.fromBitmap(bitmap, 0))



        try {
            Log.i("AWAITING", "awaiting result")
            Tasks.await(result)
            Log.i("ADDING FACES", "adding faces")
            for(face in result.result)
                facesList.add(face)

        } catch (e: Exception) {
            Log.i("FACES FAILURE", "error occured")
            Toast.makeText(context, "$e !", Toast.LENGTH_SHORT).show()
        }

//        detector.process(InputImage.fromBitmap(bitmap, 0))
//            .addOnSuccessListener{faces ->
//                Log.i("ADDING FACES", "adding faces")
//                for(face in faces)
//                    facesList.add(face)
//            }
//            .addOnFailureListener { e ->
//                Log.i("FACES FAILURE", "error occured")
//                // Handle face detection failure
//                Toast.makeText(context, "$e !", Toast.LENGTH_SHORT).show()
//            }

//        detector.process(InputImage.fromBitmap(bitmap, 0))
//            .addOnSuccessListener { faces ->
////                val bitmapCopy = bitmap.copy(Bitmap.Config.ARGB_8888, true)
////                canvas = Canvas(bitmapCopy)
////                var totalConfidenceScore = 0f
////                var numFaces = 0
////                for (face in faces) {
////                    val boundingBox = face.boundingBox
////                    val imageScore = face.smilingProbability
////
////                    // Check if the confidence score is above 80
////                    if (imageScore != null) {
////                        // Draw a rectangle around the face
////                        val rectPaint = Paint()
////                        rectPaint.color = Color.RED
////                        rectPaint.style = Paint.Style.STROKE
////                        rectPaint.strokeWidth = 3f
////
////                        canvas.drawRect(boundingBox, rectPaint)
////
////                        val textPaint = Paint()
////                        textPaint.color = Color.CYAN
////
////
////                        val canvasHeight = canvas.height
////                        val textHeight = canvasHeight / 50f
////
////                        textPaint.textSize = textHeight
////
////                        textPaint.isAntiAlias = true
//////                        textPaint.style = Paint.Style.STROKE
//////                        textPaint.strokeWidth = 1f
////
////                        val imageScorePercentage = String.format("%.2f", imageScore*100)
////                        canvas.drawText("Image Score: $imageScorePercentage%", boundingBox.left.toFloat(), boundingBox.top.toFloat(), textPaint)
////
////                        totalConfidenceScore += imageScore
////                        numFaces++
//////                        imageView.setImageBitmap(bitmapCopy)
//                Log.i("FACES DETECTED", "found faces in image")
//                for(face in faces)
//                    facesList.add(face)
//
//            }
//            .addOnFailureListener { e ->
//                Log.i("FACES FAILURE", "error occured")
//                // Handle face detection failure
//                Toast.makeText(context, "$e !", Toast.LENGTH_SHORT).show()
//            }


        Log.i("SENDING FACES ARRAY", "sending faces array within detect faces")
        return@withContext facesList
    }
    //NOT USED CURRENTLY - Begin preview function does the same job
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(view_finder.surfaceProvider)
                }

//            imageAnalysis = ImageAnalysis.Builder()
//                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                .build()
//                .also {
//                    it.setAnalyzer(cameraExecutor, FaceAnalyzer())
//                }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(requireContext()))

//
//
//
//
//        // Create a camera selector to choose the rear-facing camera
//        val cameraSelector = CameraSelector.Builder()
//            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
//            .build()
//
//        // Set up the camera preview configuration
//        val previewConfig = Preview.Builder()
//            .build()
//            .also {
//                it.setSurfaceProvider(viewFinder.surfaceProvider)
//            }
//
//        // Set up the image capture configuration
//        val imageCaptureConfig = ImageCapture.Builder()
//            .setTargetRotation(ROTATION_0)
//            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
//            .build()
//
//        // Set up the camera provider
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
//        cameraProviderFuture.addListener({
//            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
//
//            // Bind the preview and image capture use cases
//            val preview = cameraProvider.bindToLifecycle(this, cameraSelector, previewConfig)
//            val imageCapture = cameraProvider.bindToLifecycle(this, cameraSelector, imageCaptureConfig)
//
//            // Set up a listener for image capture events
//            val imageCaptureListener = object : ImageCapture.OnImageCapturedCallback() {
//                override fun onCaptureSuccess(image: ImageProxy) {
//                    // Convert the captured image to a bitmap and process it
//                    processImage(image)
//
//                    // Close the image and free up resources
//                    image.close()
//                }
//
//                override fun onError(exception: ImageCaptureException) {
//                    // Handle capture error
//                    Log.e(TAG, "Image capture failed: ${exception.message}", exception)
//                }
//            }
//
//            // Set up the image capture use case with the listener
//            imageCapture.takePicture(ContextCompat.getMainExecutor(requireContext()), imageCaptureListener)
//        }, ContextCompat.getMainExecutor(requireContext()))
    }

//    private fun processImage(image: ImageProxy) {
//        // Convert the captured image to a bitmap and process it
//        val bitmap = image.image?.toBitmap()
//        if (bitmap != null) {
//            processBitmap(bitmap)
//        }
//    }

//    private fun processBitmap(bitmap: Bitmap) {
//        // Do further tasks with the bitmap, such as face detection or image recognition
//        // ...
//    }

    //Permissions granted check
//    private fun allPermissionsGrantedCamera() = REQUIRED_PERMISSIONS_CAMERA.all {
//        if(ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this,
//                REQUIRED_PERMISSIONS_CAMERA, REQUEST_CODE_PERMISSIONS_CAMERA)
//        }
//        else {
//            return true
//        }
//    }

    //CAN BE USED - Face Detection during Live Camera
//    @ExperimentalGetImage private inner class FaceAnalyzer : ImageAnalysis.Analyzer {
//
//        private val faceDetector = FaceDetection.getClient(
//            FaceDetectorOptions.Builder()
//                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
//                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
//                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
//                .enableTracking()
//                .build()
//        )
//
//
//        override fun analyze(image: ImageProxy) {
//            val mediaImage = image.image ?: return
//
//            faceDetector.process(
//                InputImage.fromMediaImage(
//                    mediaImage,
//                    image.imageInfo.rotationDegrees
//                )
//            ).addOnSuccessListener { faces ->
//                // Handle face detection results
//            }.addOnFailureListener { e ->
//                // Handle any errors
//            }.addOnCompleteListener {
//                image.close()
//            }
//        }
//    }

    override fun onDestroyView() {
        super.onDestroyView()
//        cameraExecutor.shutdown()
//        imageAnalysis?.clearAnalyzer()
    }

    // On Permissions result Dialog
//    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == REQUEST_CODE_PERMISSIONS_CAMERA) {
//            Log.i("CAMERA PERMISSION", "inside onrequest permissions result")
//            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                // Permission has been granted, perform your file-related task here
//                    beginPreview()
//            } else {
//                // Permission has been denied, handle the situation accordingly
//                Toast.makeText(context, "Camera Permission Denied", Toast.LENGTH_SHORT).show()
//                view_finder.visibility = View.GONE
//                capture_image_button.visibility = View.GONE
//                start_camera_button.visibility = View.VISIBLE
//            }
//        }
//    }



    //This function is used to load Mobile Facenet model
    private fun loadModelFile(activity: Activity, MODEL_FILE: String): MappedByteBuffer {
        val fileDescriptor = activity.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    //Below functions - NOT USED CURRENTLY - We use the same functions as we used for Facenet model with some changes

    // For Mobile Face Net - Helper function to convert a Face object to a ByteBuffer
    fun faceToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(112 * 112 * 3 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(112 * 112)
        bitmap?.getPixels(pixels, 0, 112, 0, 0, 112, 112)
        for (pixel in pixels) {
            byteBuffer.putFloat(((pixel shr 16 and 0xFF) - 127.5f) / 127.5f)
            byteBuffer.putFloat(((pixel shr 8 and 0xFF) - 127.5f) / 127.5f)
            byteBuffer.putFloat(((pixel and 0xFF) - 127.5f) / 127.5f)
        }
        return byteBuffer
    }

    // Get the embeddings for a face using the MobileFaceNet model
//    fun getFaceEmbeddingMobile(bitmap: Bitmap): FloatArray {
//        val inputTensor = tflite.getInputTensor(0)
//        val outputTensor = tflite.getOutputTensor(0)
//        val inputBuffer = preprocessImage(bitmap)
//        val outputBuffer = ByteBuffer.allocateDirect(outputTensor.shape()[1] * 4)
//        outputBuffer.order(ByteOrder.nativeOrder())
//        tflite.run(inputBuffer, outputBuffer)
//        val embedding = FloatArray(outputTensor.shape()[1])
//        outputBuffer.rewind()
//        for (i in 0 until embedding.size) {
//            embedding[i] = outputBuffer.float
//        }
//        return embedding
//    }

    // Compare two face embeddings
    fun compareFaceEmbeddingsMobile(embedding1: FloatArray, embedding2: FloatArray): Float {
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        for (i in 0 until embedding1.size) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }
        return dotProduct / (Math.sqrt(norm1.toDouble()) * Math.sqrt(norm2.toDouble())).toFloat()
    }


    //Below functions For Facenet Model (can be used for Mobile Face Net model as well with changes to input size)

    //For Facenet

    private fun getFaceEmbedding(faceBitmap: Bitmap, interpreter: Interpreter): FloatArray {
        val inputArray = arrayOf(preprocessImage(faceBitmap))
        val outputMap = HashMap<Int, Any>()
        val outputShape = interpreter.getOutputTensor(0).shape()
        val outputSize = outputShape[1]
        val outputDataType = interpreter.getOutputTensor(0).dataType()
        val outputBuffer = ByteBuffer.allocateDirect(outputSize * 4)
        outputBuffer.order(ByteOrder.nativeOrder())
        outputMap[0] = outputBuffer
        interpreter.runForMultipleInputsOutputs(inputArray, outputMap)
        val embeddings = FloatArray(outputSize)
        outputBuffer.rewind()
//        when (outputDataType) {
//            DataType.FLOAT32 -> {
//                for (i in 0 until outputSize) {
//                    embeddings[i] = outputBuffer.float
//                }
//            }
//            DataType. -> {
//                val convertBuffer = ByteBuffer.allocateDirect(4)
//                convertBuffer.order(ByteOrder.nativeOrder())
//                for (i in 0 until outputSize) {
//                    convertBuffer.clear()
//                    convertBuffer.putShort(outputBuffer.getShort())
//                    convertBuffer.flip()
//                    embeddings[i] = convertBuffer.float
//                }
//            }
//            else -> throw Exception("Output tensor data type (${outputDataType}) is not supported")
//        }

        for (i in 0 until outputSize) {
            embeddings[i] = outputBuffer.float
        }
        return embeddings
    }

    //Preprocess image for face net

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
//        For Face Net Model
        val inputSize = 160

//        For Mobile Face Net model
//        val inputSize = 112


        val inputImage = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val byteBuffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(inputSize * inputSize)
        inputImage.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)
        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val value = intValues[pixel++]
                byteBuffer.putFloat(((value shr 16) and 0xFF) / 255f)
                byteBuffer.putFloat(((value shr 8) and 0xFF) / 255f)
                byteBuffer.putFloat((value and 0xFF) / 255f)
            }
        }
        return byteBuffer
    }

    // Face comparison using Euclidean Distance
    private fun isSimilar(embedding1: FloatArray, embedding2: FloatArray): Boolean {
        require(embedding1.size == embedding2.size) { "Embeddings must have same size" }

        var distance = 0f
        for (i in embedding1.indices) {
            distance += (embedding1[i] - embedding2[i]).pow(2)
        }
        distance = sqrt(distance)

        return distance <= 1.2
    }

    //Euclidean distance formula
    private fun euclideanDistance(embedding1: FloatArray, embedding2: FloatArray): Float {
        require(embedding1.size == embedding2.size) { "Embeddings must have same size" }

        var distance = 0f
        for (i in embedding1.indices) {
            distance += (embedding1[i] - embedding2[i]).pow(2)
        }
        distance = sqrt(distance)

        return distance
    }

    //Face comparison using Cosine Similarity
    private fun calculateCosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }

        val cosineSimilarity = dotProduct / (sqrt(norm1.toDouble()) * sqrt(norm2.toDouble()))
        return cosineSimilarity.toFloat()
    }

}
