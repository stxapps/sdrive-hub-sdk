package org.blockstack.android.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.blockstack.android.example.databinding.ActivityMainBinding
import org.blockstack.android.sdk.BaseScope
import org.blockstack.android.sdk.BlockstackSession
import org.blockstack.android.sdk.FILE_PREFIX
import org.blockstack.android.sdk.SessionStore
import org.blockstack.android.sdk.ecies.signContent
import org.blockstack.android.sdk.getBlockstackSharedPreferences
import org.blockstack.android.sdk.model.BlockstackConfig
import org.blockstack.android.sdk.model.DeleteFileOptions
import org.blockstack.android.sdk.model.GetFileOptions
import org.blockstack.android.sdk.model.PutFileOptions
import org.blockstack.android.sdk.model.UserData
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.URL

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val TAG = MainActivity::class.java.simpleName

    private val textFileName = "message.txt"
    private val imageFileName = "team.jpg"

    private var _blockstackSession: BlockstackSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val config = BlockstackConfig(
            URI("https://flamboyant-darwin-d11c17.netlify.app"),
            "/redirect",
            "/manifest.json",
            arrayOf(BaseScope.StoreWrite.scope)
        )

        val sessionStore = SessionStore(getBlockstackSharedPreferences())
        _blockstackSession = BlockstackSession(sessionStore, config)

        val isSignedIn = blockstackSession().isUserSignedIn()
        if (isSignedIn) {
            val userData = blockstackSession().loadUserData()
            onSignIn(userData)
        } else {
            onSignOut()
        }

        binding.content.signInButton.setOnClickListener {
            val userData = UserData(
                JSONObject()
                    .put("decentralizedID", "")
                    .put("identityAddress", "")
                    .put("appPrivateKey", "")
                    .put("hubUrl", "")
                    .put("gaiaAssociationToken", "")
            )
            lifecycleScope.launch {
                blockstackSession().updateUserData(userData)
                onSignIn(userData)
            }
        }

        binding.content.signOutButton.setOnClickListener {
            blockstackSession().signUserOut()
            onSignOut()
        }

        binding.content.getStringFileButton.setOnClickListener {
            binding.content.fileContentsTextView.text = "Downloading..."
            val options = GetFileOptions(decrypt = false)
            lifecycleScope.launch {
                val contentResult = blockstackSession().getFile(textFileName, options)
                if (contentResult.hasValue) {
                    val content = contentResult.value!!
                    Log.d(TAG, "File contents: $content")
                    binding.content.fileContentsTextView.text = content as String

                } else {
                    Toast.makeText(this@MainActivity, "error: ${contentResult.error}", Toast.LENGTH_SHORT).show()
                }

            }
        }

        binding.content.deleteStringFileButton.setOnClickListener {
            binding.content.deleteFileMessageTextView.text = "Deleting..."
            lifecycleScope.launch {
                val deleteResult = blockstackSession().deleteFile(textFileName, DeleteFileOptions())
                if (deleteResult.hasErrors) {
                    Toast.makeText(this@MainActivity, "error " + deleteResult.error, Toast.LENGTH_SHORT).show()
                } else {
                    binding.content.deleteFileMessageTextView.text = "File $textFileName deleted."
                }
            }
        }

        binding.content.putStringFileButton.setOnClickListener {
            binding.content.readURLTextView.text = "Uploading..."
            val options = PutFileOptions(encrypt = false)
            lifecycleScope.launch {

                val readURLResult = blockstackSession().putFile(textFileName, "Hello Android!", options)
                if (readURLResult.hasValue) {
                    val readURL = readURLResult.value!!
                    Log.d(TAG, "File stored at: $readURL")
                    binding.content.readURLTextView.text = "File stored at: $readURL"

                } else {
                    Toast.makeText(this@MainActivity, "error: ${readURLResult.error}", Toast.LENGTH_SHORT).show()
                }

            }
        }

        binding.content.putImageFileButton.setOnClickListener {
            binding.content.imageFileTextView.text = "Uploading..."

            val drawable: BitmapDrawable = ContextCompat.getDrawable(this, R.drawable.blockstackteam) as BitmapDrawable

            val bitmap = drawable.bitmap
            val stream = ByteArrayOutputStream()

            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            val bitMapData = stream.toByteArray()

            val options = PutFileOptions(true)
            lifecycleScope.launch {
                val readURLResult = blockstackSession().putFile(imageFileName, bitMapData, options)
                if (readURLResult.hasValue) {
                    val readURL = readURLResult.value!!
                    Log.d(TAG, "File stored at: $readURL")
                    binding.content.imageFileTextView.text = "File stored at: $readURL"

                } else {
                    Toast.makeText(this@MainActivity, "error: ${readURLResult.error}", Toast.LENGTH_SHORT).show()
                }

            }
        }

        binding.content.getImageFileButton.setOnClickListener {
            val options = GetFileOptions(decrypt = true)
            lifecycleScope.launch {
                val contentsResult = blockstackSession().getFile(imageFileName, options)
                if (contentsResult.hasValue) {
                    val contents = contentsResult.value!!
                    val imageByteArray = contents as ByteArray
                    val bitmap = BitmapFactory.decodeByteArray(imageByteArray, 0, imageByteArray.size)
                    binding.content.imageView.setImageBitmap(bitmap)
                } else {
                    Toast.makeText(this@MainActivity, "error: ${contentsResult.error}", Toast.LENGTH_SHORT).show()
                }

            }
        }

        binding.content.deleteImageFileButton.setOnClickListener {
            Log.d(TAG, "Deleting...")
            lifecycleScope.launch {
                val deleteResult = blockstackSession().deleteFile(imageFileName, DeleteFileOptions())
                if (deleteResult.hasErrors) {
                    Toast.makeText(this@MainActivity, "error " + deleteResult.error, Toast.LENGTH_SHORT).show()
                } else {
                    binding.content.imageView.setImageBitmap(null)
                    Log.d(TAG, "File $imageFileName deleted.")
                }
            }
        }

        binding.content.performFilesButton.setOnClickListener {
            //val pfData = """{"values":[{"id":"1708491132374-hjJQ-qGLN-1708491136062","type":"putFile","path":"links/1707816556114-IeqP/1708491132374-hjJQ-qGLN-1708491136062.json","content":"{\"id\":\"1708491132374-hjJQ-qGLN-1708491136062\",\"url\":\"www.lyft.com\",\"addedDT\":1708491132374,\"decor\":{\"image\":{\"bg\":{\"type\":\"image\",\"value\":\"/static/media/silver-framed-eyeglasses-beside-white-click-pen-and-white-notebook.43cbd30b.jpg\"},\"fg\":null},\"favicon\":{\"bg\":{\"type\":\"color\",\"value\":\"bg-teal-300\"}}},\"extractedResult\":{\"url\":\"http://www.lyft.com\",\"status\":\"EXTRACT_OK\",\"title\":\"Lyft: A ride whenever you need one\",\"image\":\"https://images.ctfassets.net/q8mvene1wzq4/3amVLJGrSSKSYmDbFOCn9C/f7133270e145473d34a76d583294841d/04__2x.png\",\"extractedDT\":1705309222422}}"}],"isSequential":false,"nItemsForNs":10}"""
            //val pfData = """{"values":[{"values":[{"id":"images/1708491132374-hjJQ-vets-1708496809761.jpg","type":"putFile","path":"file://images/1708491132374-hjJQ-vets-1708496809761.jpg","content":""}],"isSequential":false,"nItemsForNs":10},{"id":"links/1707816556114-IeqP/1708491132374-hjJQ-UHxX-1708496809781.json","type":"putFile","path":"links/1707816556114-IeqP/1708491132374-hjJQ-UHxX-1708496809781.json","content":"{\"id\":\"1708491132374-hjJQ-UHxX-1708496809781\",\"url\":\"www.lyft.com\",\"addedDT\":1708491132374,\"decor\":{\"image\":{\"bg\":{\"type\":\"image\",\"value\":\"/static/media/silver-framed-eyeglasses-beside-white-click-pen-and-white-notebook.43cbd30b.jpg\"},\"fg\":null},\"favicon\":{\"bg\":{\"type\":\"color\",\"value\":\"bg-teal-300\"}}},\"extractedResult\":{\"url\":\"http://www.lyft.com\",\"status\":\"EXTRACT_OK\",\"title\":\"Lyft: A ride whenever you need one\",\"image\":\"https://images.ctfassets.net/q8mvene1wzq4/3amVLJGrSSKSYmDbFOCn9C/f7133270e145473d34a76d583294841d/04__2x.png\",\"extractedDT\":1705309222422},\"custom\":{\"title\":\"Lyft --- bla bla bla\",\"image\":\"cdroot/images/1708491132374-hjJQ-vets-1708496809761.jpg\"}}"}],"isSequential":true,"nItemsForNs":10}"""
            val pfData = """{"values":[{"values":[{"values":[],"isSequential":false,"nItemsForNs":10},{"values":[{"id":"links/1707816556114-IeqP/1708491132374-hjJQ-UHxX-1708496809781.json","type":"deleteFile","path":"links/1707816556114-IeqP/1708491132374-hjJQ-UHxX-1708496809781.json","doIgnoreDoesNotExistError":true}],"isSequential":false,"nItemsForNs":10}],"isSequential":true,"nItemsForNs":10}],"isSequential":false,"nItemsForNs":10}"""
            val dir = this.filesDir.absolutePath

            lifecycleScope.launch {
                val results = blockstackSession().performFiles(pfData, dir)
                if (results.hasValue) {
                    Log.d(TAG, "performFiles results: ${results.value}")
                } else {
                    Toast.makeText(this@MainActivity, "error: ${results.error}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.content.listFilesButton.setOnClickListener {
            binding.content.listFilesText.text = "...."
            lifecycleScope.launch {
                val countResult = blockstackSession().listFiles { urlResult ->
                    if (urlResult.hasValue) {
                        if (binding.content.listFilesText.text === "....") {
                            binding.content.listFilesText.text = urlResult.value
                        } else {
                            binding.content.listFilesText.text = binding.content.listFilesText.text.toString() + "\n" + urlResult.value
                        }
                    }
                    true
                }

                Log.d(TAG, "files count " + if (countResult.hasValue) {
                    countResult.value
                } else {
                    countResult.error
                })


            }
        }

        binding.content.putLocalFileButton.setOnClickListener {
            val drawable: BitmapDrawable = ContextCompat.getDrawable(this, R.drawable.blockstackteam) as BitmapDrawable

            val bitmap = drawable.bitmap
            val stream = ByteArrayOutputStream()

            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            val bitMapData = stream.toByteArray()

            val fpath = this.filesDir.absolutePath + "/" + imageFileName
            Log.d(TAG, "Local file path: $fpath")

            //val dir = File(this.filesDir.absolutePath + "/images")
            //if (!dir.exists()) dir.mkdirs()

            val pathUri = Uri.parse(fpath)
            val file = File(pathUri.path!!)
            file.writeBytes(bitMapData)
            Log.d(TAG, "Saved the local file")

            val options = PutFileOptions(true, dir = this.filesDir.absolutePath)
            lifecycleScope.launch {
                val readURLResult = blockstackSession().putFile(FILE_PREFIX + imageFileName, "", options)
                if (readURLResult.hasValue) {
                    val readURL = readURLResult.value!!
                    Log.d(TAG, "File stored at: $readURL")
                } else {
                    Toast.makeText(this@MainActivity, "error: ${readURLResult.error}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.content.getLocalFileButton.setOnClickListener {
            val fpath = this.filesDir.absolutePath + "/" + imageFileName
            val pathUri = Uri.parse(fpath)
            val file = File(pathUri.path!!)
            if (file.exists()) file.deleteRecursively()
            Log.d(TAG, "Is local file exist: ${file.exists()}")

            val options = GetFileOptions(decrypt = true, dir = this.filesDir.absolutePath)
            lifecycleScope.launch {
                val contentsResult = blockstackSession().getFile(FILE_PREFIX + imageFileName, options)
                if (contentsResult.hasValue) {
                    val contents = contentsResult.value!!
                    Log.d(TAG, "Saved local file with contents: ${contents}")

                    val imageByteArray = file.inputStream().use { it.readBytes() }
                    val bitmap = BitmapFactory.decodeByteArray(imageByteArray, 0, imageByteArray.size)
                    binding.content.imageView.setImageBitmap(bitmap)
                } else {
                    Toast.makeText(this@MainActivity, "error: ${contentsResult.error}", Toast.LENGTH_SHORT).show()
                }

            }
        }

        binding.content.signECDSAButton.setOnClickListener {
            val appPrivateKey = ""
            val obj = signContent("Privacy Security UX", appPrivateKey, true)
            Log.d(TAG, "Public key: ${obj.publicKey}, Signature: ${obj.signature}")
            binding.content.signECDSAText.text = "Public key: ${obj.publicKey}, Signature: ${obj.signature}"
        }
    }

    private fun onSignIn(userData: UserData) {
        binding.content.userDataTextView.text = "Signed in as ${userData.decentralizedID}"
        showUserAvatar(userData.profile?.avatarImage)

        binding.content.signInButton.isEnabled = false
        binding.content.signOutButton.isEnabled = true
        binding.content.getStringFileButton.isEnabled = true
        binding.content.putStringFileButton.isEnabled = true
        binding.content.deleteStringFileButton.isEnabled = true
        binding.content.getImageFileButton.isEnabled = true
        binding.content.putImageFileButton.isEnabled = true
        binding.content.deleteImageFileButton.isEnabled = true
        binding.content.performFilesButton.isEnabled = true
        binding.content.listFilesButton.isEnabled = true
        binding.content.putLocalFileButton.isEnabled = true
        binding.content.getLocalFileButton.isEnabled = true
    }

    private fun showUserAvatar(avatarImage: String?) {
        if (avatarImage != null) {
            // use whatever suits your app architecture best to asynchronously load the avatar
            // better use a image loading library than the code below
            GlobalScope.launch(Dispatchers.Main) {
                val avatar = withContext(Dispatchers.IO) {
                    try {
                        BitmapDrawable.createFromStream(URL(avatarImage).openStream(), "src")
                    } catch (e: Exception) {
                        Log.d(TAG, e.toString())
                        null
                    }
                }
                binding.content.avatarView.setImageDrawable(avatar)
            }
        } else {
            binding.content.avatarView.setImageResource(R.drawable.default_avatar)
        }
    }

    private fun onSignOut() {
        binding.content.userDataTextView.text = "Signed out"
        binding.content.avatarView.setImageDrawable(null)

        binding.content.signInButton.isEnabled = true
        binding.content.signOutButton.isEnabled = false
        binding.content.getStringFileButton.isEnabled = false
        binding.content.putStringFileButton.isEnabled = false
        binding.content.deleteStringFileButton.isEnabled = false
        binding.content.getImageFileButton.isEnabled = false
        binding.content.putImageFileButton.isEnabled = false
        binding.content.deleteImageFileButton.isEnabled = false
        binding.content.performFilesButton.isEnabled = false
        binding.content.listFilesButton.isEnabled = false
        binding.content.putLocalFileButton.isEnabled = false
        binding.content.getLocalFileButton.isEnabled = false
    }

    private fun blockstackSession(): BlockstackSession {
        val session = _blockstackSession
        if (session != null) {
            return session
        } else {
            throw IllegalStateException("No session.")
        }
    }

}
