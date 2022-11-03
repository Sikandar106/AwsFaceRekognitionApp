package com.example.awsfacerekognitionsample

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.View.OnClickListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.amazonaws.AmazonClientException
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.rekognition.AmazonRekognition
import com.amazonaws.services.rekognition.AmazonRekognitionClient
import com.amazonaws.services.rekognition.model.*
import com.example.awsfacerekognitionsample.databinding.ActivityMainBinding
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.io.IOUtils
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlin.system.exitProcess


class MainActivity : AppCompatActivity(), OnClickListener {

    private lateinit var binding: ActivityMainBinding
    private var sourceImagePath: String = ""
    private var targetImagePath = ""
    private lateinit var getPathHelper: UriPathHelper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initializer()
    }





    private fun initializer() {
        askRuntimePermission()
        getPathHelper = UriPathHelper()
        setListeners()
    }

    //REGISTER LISTENERS FOR CLICK EVENT
    private fun setListeners() {
        binding.sourceImage.setOnClickListener(this)
        binding.targertImage.setOnClickListener(this)
        binding.buttonTest.setOnClickListener(this)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onClick(p0: View?) {
        when (p0?.id) {
            binding.sourceImage.id -> {
                selectImage(121, 212)
            }
            binding.targertImage.id -> {
                selectImage(222, 111)
            }
            binding.buttonTest.id -> {
                binding.icNotEqual.visibility = View.GONE
                binding.icEqual.visibility = View.GONE
                if (sourceImagePath.isEmpty() && targetImagePath.isEmpty()) {
                    Toast.makeText(
                        this,
                        "kindly upload source and target image to see the result",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    binding.progressBar.visibility = View.VISIBLE
                    GlobalScope.launch(Dispatchers.Default) {
                        compareFaces(70f, sourceImagePath, targetImagePath)
                    }
                }
            }
        }
    }

    //SELECT IMAGE FROM CAMERA OR GALLERY GENERIC FUNCTION
    private fun selectImage(cameraRequestCode: Int, galleryRequestCode: Int) {
        val options = arrayOf<CharSequence>("Take Photo", "Choose from Gallery", "Cancel")
        val builder: AlertDialog.Builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("Add Photo!")
        builder.setItems(options, DialogInterface.OnClickListener { dialog, item ->
            if (options[item] == "Take Photo") {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivityForResult(intent, cameraRequestCode)
            } else if (options[item] == "Choose from Gallery") {
                val intent =
                    Intent(Intent.ACTION_PICK)
                intent.type = "image/*"
                startActivityForResult(intent, galleryRequestCode)
            } else if (options[item] == "Cancel") {
                dialog.dismiss()
            }
        })
        builder.show()
    }

    //GETTING PICTURE FROM CAMERA OR GALLERY AND SET INTO IMAGEVIEW
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            when (requestCode) {
                121 -> {
                    val selectedImage = data.extras?.get("data") as Bitmap
                    binding.imageViewSource.setImageBitmap(selectedImage)
                    val getImageUri = getImageUriFromBitmap(this,selectedImage)
                    sourceImagePath = getPathHelper.getPath(this,getImageUri).toString()
                }
                212 -> {
                    val galleryImg = data.data
                    binding.imageViewSource.setImageURI(galleryImg)
                    sourceImagePath =
                        galleryImg?.let { getPathHelper.getPath(this, it).toString() }.toString()
                }
                222 -> {
                    val selectedImageTarget = data.extras?.get("data") as Bitmap
                    binding.imageViewTarget.setImageBitmap(selectedImageTarget)
                    val getImageUri = getImageUriFromBitmap(this,selectedImageTarget)
                    targetImagePath = getPathHelper.getPath(this,getImageUri).toString()
                }
                111 -> {
                    val galleryImgTarget = data.data
                    binding.imageViewTarget.setImageURI(galleryImgTarget)
                    targetImagePath =
                        galleryImgTarget?.let { getPathHelper.getPath(this, it).toString() }
                            .toString()
                }
            }
        }
    }

    private fun getImageUriFromBitmap(context: Context, bitmap: Bitmap): Uri{
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(context.contentResolver, bitmap, "Title", null)
        return Uri.parse(path.toString())
    }

    //USING AWS REKOGNITION SERVICE FOR COMPARING FACE FROM SOURCE TO TARGET IMAGE
    @OptIn(DelicateCoroutinesApi::class)
    private fun compareFaces(
        similarityThreshold: Float,
        sourceImage: String,
        targetImage: String
    ) {

        var sourceImageBytes: ByteBuffer?
        var targetImageBytes: ByteBuffer?

        val rekognitionClient: AmazonRekognition = AmazonRekognitionClient(
            BasicAWSCredentials(
                "ACCESSKEY",
                "SECRETKEY"
            )
        )

        //Load source and target images and create input parameters

        //Load source and target images and create input parameters
        try {
            FileInputStream(File(sourceImage)).use { inputStream ->
                sourceImageBytes =
                    ByteBuffer.wrap(IOUtils.toByteArray(inputStream))
            }
        } catch (e: Exception) {
            println("Failed to load source image $sourceImage")
            exitProcess(1)
        }

        try {
            FileInputStream(File(targetImage)).use { inputStream ->
                targetImageBytes =
                    ByteBuffer.wrap(IOUtils.toByteArray(inputStream))
            }
        } catch (e: Exception) {
            println("Failed to load target images: $targetImage")
            exitProcess(1)
        }



        try {
            val source: Image = Image()
                .withBytes(sourceImageBytes)
            val target: Image = Image()
                .withBytes(targetImageBytes)

            val request: CompareFacesRequest = CompareFacesRequest()
                .withSourceImage(source)
                .withTargetImage(target)
                .withSimilarityThreshold(similarityThreshold)

            // Call operation

            // Call operation
            val compareFacesResult: CompareFacesResult = rekognitionClient.compareFaces(request)
            // Display results
            val faceDetails: List<CompareFacesMatch> = compareFacesResult.faceMatches
            binding.progressBar.visibility = View.INVISIBLE
            if (faceDetails.isNotEmpty()) {
                for (match: CompareFacesMatch in faceDetails) {
                    if (match.face != null) {
                        val face: ComparedFace = match.face
                        val position: BoundingBox = face.boundingBox
                        println(
                            "Face at " + position.left.toString() + " " + position.top
                                .toString() + " matches with " + match.similarity.toString() + "% confidence."
                        )
                        GlobalScope.launch(Dispatchers.Main) {
                            binding.icNotEqual.visibility = View.INVISIBLE
                            binding.icEqual.visibility = View.VISIBLE
                            Toast.makeText(
                                this@MainActivity,
                                "Face in the source image has matched with the face in target image",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } else {
                GlobalScope.launch(Dispatchers.Main) {
                    binding.icNotEqual.visibility = View.VISIBLE
                    binding.icEqual.visibility = View.INVISIBLE
                    Toast.makeText(
                        this@MainActivity,
                        "Face in the source image did not match with the face in target image",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: InvalidImageFormatException) {
            GlobalScope.launch(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Kindly Upload Valid Image", Toast.LENGTH_SHORT).show()

            }
        }
    }


    //ASKING RUNTIME PERMISSION FROM USER
    private fun askRuntimePermission() {
            Dexter.withContext(this).withPermissions(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(p0: MultiplePermissionsReport?) {
                    if (p0 != null) {
                        if (p0.areAllPermissionsGranted())
                            Toast.makeText(
                                this@MainActivity,
                                "all permission granted",
                                Toast.LENGTH_SHORT
                            ).show()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "not all permission granted to user",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: MutableList<PermissionRequest>?,
                    p1: PermissionToken?
                ) {

                }
            }).check()
        }
    }
