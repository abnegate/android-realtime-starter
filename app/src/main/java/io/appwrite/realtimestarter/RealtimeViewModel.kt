package io.appwrite.realtimestarter

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import io.appwrite.Client
import io.appwrite.extensions.fromJson
import io.appwrite.extensions.toJson
import io.appwrite.services.Account
import io.appwrite.services.Database
import io.appwrite.services.Realtime
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class RealtimeViewModel : ViewModel() {

    private val collectionId = "611b6bfb93f41" // Replace with your own products collection id
    private val userEmail = "jake@appwrite.io" // Replace with your own user email
    private val userPassword = "password"

    private val realtime by lazy {
        Realtime(client!!)
    }

    private val account by lazy {
        Account(client!!)
    }

    private val db by lazy {
        Database(client!!)
    }

    private val _productStream = MutableLiveData<Product>()
    val productStream: LiveData<Product> = _productStream

    private var client: Client? = null

    fun subscribeToProducts(context: Context) {
        if (client == null) {
            client = Client(context)
                .setEndpoint("http://192.168.20.4/v1") // Replace with your own endpoint
                .setProject("611b6959a3afb") // Replace with your project id
                .setSelfSigned(true)
        }

        GlobalScope.launch {
            // Create a session so that we are authorized for realtime
            account.createSession(userEmail, userPassword)

            // Attach an error logger to the realtime instace
            realtime.doOnError {
                Log.e("Realtime", it.message.toString())
            }

            // Subscribe for realtime updates
            realtime.subscribe("collections.${collectionId}.documents") {

                // Parse the incoming realtime update to a product from JSON
                val product = try {
                    it.toJson().fromJson(Product::class.java)
                } catch (ex: JsonParseException) {
                    Log.e("Parse product", ex.message.toString())
                    return@subscribe
                } catch (ex: JsonSyntaxException) {
                    Log.e("Parse product", ex.message.toString())
                    return@subscribe
                }

                // Post the new product to stream observers
                _productStream.postValue(product)
            }

            // Useful for testing; insert 100 products while subscribed
            for (i in 1 until 100) {
                db.createDocument(
                    collectionId,
                    """{ "name": "iPhone $i", "sku":"iphone$i", "price": $i, "image_url": "https://dummyimage.com/600x400/cde/fff" }""",
                    listOf("*"),
                    listOf("*")
                )
            }
        }
    }
}