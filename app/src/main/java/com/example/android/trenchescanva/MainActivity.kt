package com.example.android.trenchescanva

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.core.view.iterator
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

@Suppress("BlockingMethodInNonBlockingContext")
class MainActivity : AppCompatActivity() {
    private var drawingView: DrawingView? = null
    private var mImageButtonCurrentPaint: ImageButton? = null
    private var linearLayoutPalette: LinearLayout? = null
    var customProgress: Dialog? = null
    private val openGalleryLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        if (it.resultCode == RESULT_OK && it.data != null){
            val backgroundImage: ImageView = findViewById(R.id.iv_background)
            backgroundImage.setImageURI(it.data?.data)
        }
    }
    private val photoResultLauncher: ActivityResultLauncher<Array<String>> = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ permissions ->
        permissions.entries.forEach {
            val permissionName = it.key
            val permissionGranted = it.value
            if (permissionGranted && permissionName == Manifest.permission.READ_EXTERNAL_STORAGE){
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
                val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                openGalleryLauncher.launch(pickIntent)
            }else{
                if (permissionName == Manifest.permission.READ_EXTERNAL_STORAGE)
                    Toast.makeText(this, "You denied permission. To change this, go to your settings", Toast.LENGTH_SHORT).show()
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        drawingView = findViewById(R.id.drawing_view)
        val galleryBtn: ImageButton = findViewById(R.id.gallery_btn)
        val brushSizeBtn = findViewById<ImageButton>(R.id.brush_size_button)
        val undoBtn: ImageButton = findViewById(R.id.undo_btn)
        val redoBtn: ImageButton = findViewById(R.id.redo_btn)
        val saveBtn: ImageButton = findViewById(R.id.save_btn)

        galleryBtn.setOnClickListener {
            requestStoragePermission()
        }
        brushSizeBtn.setOnClickListener {
            showBrushSizeChooserDialog()
        }
        undoBtn.setOnClickListener {
            drawingView?.onClickUndo()
        }
        redoBtn.setOnClickListener {
            drawingView?.onClickRedo()
        }
        saveBtn.setOnClickListener{
            if (isPermissionGranted()){
                showProgressDialog()
                lifecycleScope.launch{
                    val frameLayout: FrameLayout = findViewById(R.id.frame_drawing_container)
                    saveBitmapToDevice(getBitmapFromView(frameLayout))
                }
            }
        }
        linearLayoutPalette = findViewById(R.id.color_palette_layout)
        (linearLayoutPalette!![1] as ImageButton).setImageDrawable(ContextCompat.getDrawable(this, R.drawable.palette_pressed))
    }

    fun resetColorPaletteBtn(){
        for(color in linearLayoutPalette!!){
            (color as ImageButton).setImageDrawable(ContextCompat.getDrawable(this, R.drawable.palette_normal))
        }
    }

    fun paintClicked(view: View){
        if (view != mImageButtonCurrentPaint) {
            resetColorPaletteBtn()
            val imageButton = view as ImageButton
            imageButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.palette_pressed))
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)
        }
    }

    private fun showAlertDialog(title: String, message: String){
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("Cancel"){dialog, _->
            dialog.dismiss()
        }
        builder.create().show()
    }

    private fun showProgressDialog(){
        customProgress = Dialog(this@MainActivity)

        customProgress?.setContentView(R.layout.dialog_custom_progress)
        customProgress?.show()
    }

    private fun cancelProgressDialog(){
        if(customProgress != null){
            customProgress?.dismiss()
            customProgress = null
        }
    }

    private fun requestStoragePermission(): Boolean{
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && shouldShowRequestPermissionRationale(
                Manifest.permission.READ_EXTERNAL_STORAGE)) {
            showAlertDialog(
                "Trenches Canva requires storage access",
                "Change the permission settings for Trenches Canva to continue"
            )
            return false
        }else{
            photoResultLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            return true
        }
    }
    private fun showBrushSizeChooserDialog(){
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.brush_size_dialog)
        brushDialog.setTitle("Brush size: ")
        val smallBtn = brushDialog.findViewById<ImageButton>(R.id.ib_small_brush)
        smallBtn.setOnClickListener{
            drawingView!!.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }
        val mediumBtn = brushDialog.findViewById<ImageButton>(R.id.ib_medium_brush)
        mediumBtn.setOnClickListener {
            drawingView!!.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        val largeBtn = brushDialog.findViewById<ImageButton>(R.id.ib_large_brush)
        largeBtn.setOnClickListener {
            drawingView!!.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }

        brushDialog.show()
    }

    private fun getBitmapFromView (view: View): Bitmap{
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if (bgDrawable != null){
            bgDrawable.draw(canvas)
        } else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)

        return returnedBitmap
    }

    private fun isPermissionGranted(): Boolean {
        val isAllowed = ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        return isAllowed == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun saveBitmapToDevice(mBitmap: Bitmap){
        var result: String
        withContext(Dispatchers.IO){
            try{
                val bytes = ByteArrayOutputStream()
                mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

               /* val fileName = File(externalCacheDir?.absoluteFile.toString() + File.separator + "TrenchesCanva_" + System.currentTimeMillis()/1000 + ".png")
                val fOS = FileOutputStream(fileName) */
                val file: File = Environment.getExternalStorageDirectory()
                val dir = File(file.absolutePath + "/Pictures")
                dir.mkdirs()
                val fileName: String = String.format( "${System.currentTimeMillis()/1000.0}.png")
                val outFile = File(dir, fileName)
                val fOS = FileOutputStream(outFile)
                fOS.write(bytes.toByteArray())
                fOS.close()

                result = outFile.absolutePath
                runOnUiThread{
                    cancelProgressDialog()
                    if (result.isNotEmpty()){
                        Toast.makeText(this@MainActivity, "Image saved successfully: $result", Toast.LENGTH_SHORT).show()
                        shareImage(result)
                    } else{
                        Toast.makeText(this@MainActivity,"Something went wrong saving your image", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception){
                result = ""
                e.printStackTrace()
            }
        }
    }

    private fun shareImage(imgPath: String){
        MediaScannerConnection.scanFile(this@MainActivity, arrayOf(imgPath), null){
            path, uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent, "Share"))
        }
    }

}