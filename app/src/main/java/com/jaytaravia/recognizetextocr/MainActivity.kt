package com.jaytaravia.recognizetextocr

import android.app.Activity
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : AppCompatActivity() {

    //UI Views
    private lateinit var inputImageBtn: MaterialButton
    private lateinit var recognizeTextBtn: MaterialButton
    private lateinit var imageIv: ImageView
    private lateinit var recognizedTextEt: EditText

    private companion object{
        //to handle the result of Camera/Gallery permissions in onRequestPermissionResults
        private const val CAMERA_REQUEST_CODE = 100
        private const val STORAGE_REQUEST_CODE = 101
    }

    //Uri of the image that we will take from Camera/Gallery
    private var imageUri: Uri? = null

    //arrays of permissions required to pick image from Camera/Gallery
    private lateinit var cameraPermissions: Array<String>
    private lateinit var storagePermissions: Array<String>


    private lateinit var progressDialog: ProgressDialog

    private lateinit var textRecognizer : TextRecognizer

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //init UI Views
        inputImageBtn = findViewById(R.id.inputImageBtn)
        recognizeTextBtn = findViewById(R.id.recognizeTextBtn)
        imageIv = findViewById(R.id.imageIv)
        recognizedTextEt = findViewById(R.id.recognizedTextEt)

        //init arrays of permissions required for Camera, Gallery
        cameraPermissions = arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.READ_MEDIA_IMAGES)
        storagePermissions = arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)

        //init setup the progress dialog, show while text from image is being recognized
        progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Please wait")
        progressDialog.setCanceledOnTouchOutside(false)

        //init TextRecognizer
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)


        //handle click, show input image dialog
        inputImageBtn.setOnClickListener {
            showInputImageDialog()
        }

        recognizeTextBtn.setOnClickListener {

            if (imageUri == null){

                showToast("Pick Image First...")
            }
            else{

                recognizeTextFromImage()
            }
        }
    }

    private fun recognizeTextFromImage() {
        progressDialog.setMessage("Preparing Image...")
        progressDialog.show()

        try {

            val inputImage = InputImage.fromFilePath(this, imageUri!!)

            progressDialog.setMessage("Recognizing text...")

            val textTaskResult = textRecognizer.process(inputImage)
                .addOnSuccessListener { text ->

                    progressDialog.dismiss()

                    val recognizedText = text.text

                    recognizedTextEt.setText(recognizedText)
                }
                .addOnFailureListener {e->

                    progressDialog.dismiss()
                    showToast("Failed to recognize text due to ${e.message}")
                }
        }
        catch (e: Exception){

            progressDialog.dismiss()
            showToast("Faliled to prepare image due to ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun showInputImageDialog() {
        //init PopupMenu param 1 is context, param 2 is UI View where you want to show PopupMenu
        val popupMenu = PopupMenu(this, inputImageBtn)

        //Add items Camera, Gallery to PopupMenu, param 2 is menu id, param 3 is position of this menu item in menu items list, param 4 is title of the menu
        popupMenu.menu.add(Menu.NONE, 1, 1, "CAMERA")
        popupMenu.menu.add(Menu.NONE, 2, 2, "GALLERY")

        //Show PopupMenu
        popupMenu.show()

        //handle PopupMenu item clicks
        popupMenu.setOnMenuItemClickListener {menuItem->
            //get item id that is clicked from PopupMenu
            val id = menuItem.itemId
            if (id == 1){
                //Camera is clicked, check if camera permissions are granted or not
                if (checkCameraPermissions()){
                    //camera permissions granted, we can launch camera intent
                    pickImageCamera()
                }
                else{
                    //camera permissions not granted, request the camera permissions
                    requestCameraPermissions()
                }
            }else if (id == 2){
                //Gallery is clicked, check if storage permission is granted or not
                if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        checkStoragePermission()
                    } else {
                        TODO("VERSION.SDK_INT < TIRAMISU")
                    }
                ){
                    //storage permission granted, we can launch the gallery intent
                    pickImageGallery()
                }
                else{
                    //storage permission not granted, request the storage permission
                    requestStoragePermission()
                }
            }

            return@setOnMenuItemClickListener true
        }
    }

    private fun pickImageGallery(){
        //intent to pick image from gallery. will show all resources from where we can pick the image
        val intent = Intent(Intent.ACTION_PICK)
        //set type of file we want to pick i.e. image
        intent.type = "image/*"
        galleryActivityResultLauncher.launch(intent)
    }

    private val galleryActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
            //here we will receive the image, if picked
            if (result.resultCode == Activity.RESULT_OK){
                //image picked
                val data = result.data
                imageUri = data!!.data
                //set to imageView i.e imageIv
                imageIv.setImageURI(imageUri)
            }
            else{
                //Cancelled
                showToast("Cancelled...!")
            }
        }

    private fun pickImageCamera(){
        //get ready the image data to store in MediaStore
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, "Sample Title")
        values.put(MediaStore.Images.Media.DESCRIPTION, "Sample Description")
        //image Uri
        imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        //intent to launch camera
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
        cameraActivityResultLauncher.launch(intent)

    }

    private val cameraActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result ->
            //here we will receive the image, if taken from camera
            if (result.resultCode == Activity.RESULT_OK){

                //image is taken from camera
                //we already have the image in imageUri using function pickImageCamera
                imageIv.setImageURI(imageUri)
            }
            else{
                //cancelled
                showToast("Cancelled...")
            }
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkStoragePermission() : Boolean{
        /*check if storage permission is allowed or not
        * return true if allowed, false if not allowed*/
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkCameraPermissions() : Boolean{
        /*check if camera & storage permissions are allowed or not
        * return true if allowed, false if not allowed*/
        val cameraResult = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val storageResult = ContextCompat.checkSelfPermission(this,android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED

        return cameraResult && storageResult
    }

    private fun requestStoragePermission(){
        //request storage permission (for gallery image pick)
        ActivityCompat.requestPermissions(this, storagePermissions, STORAGE_REQUEST_CODE)
    }

    private fun requestCameraPermissions(){
        //request camera permissions (for camera intent)
        ActivityCompat.requestPermissions(this, cameraPermissions, CAMERA_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //handle permission(s) results
        when(requestCode){
            CAMERA_REQUEST_CODE ->{
                //Check if some action from permission dialog performed or not Allow/Deny
                if (grantResults.isNotEmpty()){
                    //Check if Camera, Storage permissions granted, contains boolean results either true or false
                    val cameraAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED
                    val storageAccepted = grantResults[1] == PackageManager.PERMISSION_GRANTED
                    //Check if both permissions are granted or not
                    if (cameraAccepted && storageAccepted){
                        //both permissions (Camera & Gallery) are granted, we can launch camera intent
                        pickImageCamera()
                    }else{
                        //one or both permissions are denied, can't launch camera intent
                        showToast("Camera & Storage permission are required...")
                    }
                }
            }
            STORAGE_REQUEST_CODE ->{
                //Check if some action from permission dialog performed or not Allow/Deny
                if (grantResults.isNotEmpty()){
                    //Check if Storage permissions granted, contains boolean results either true or false
                    val storageAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED
                    //Check if storage permissions is granted or not
                    if (storageAccepted){
                        //storage permissions granted, we can launch gallery intent
                        pickImageGallery()
                    }
                    else{
                        //storage permissions denied, can't launch gallery intent
                        showToast("Storage permission is required...")
                    }
                }
            }
        }
    }


    private fun showToast(message: String){
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}