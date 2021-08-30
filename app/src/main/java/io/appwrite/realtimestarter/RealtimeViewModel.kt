package io.appwrite.realtimestarter

import android.content.Context
import android.util.Log
import androidx.lifecycle.*
import io.appwrite.Client
import io.appwrite.extensions.toJson
import io.appwrite.models.RealtimeResponseEvent
import io.appwrite.models.RealtimeSubscription
import io.appwrite.services.Account
import io.appwrite.services.Database
import io.appwrite.services.Realtime
import kotlinx.coroutines.launch

class RealtimeViewModel : ViewModel(), LifecycleObserver {

    private val endpoint = "https://realtime.appwrite.org/v1" // Replace with your endpoint
    private val projectId = "611cc1d3aeb03"         // Replace with your project ID
    private val collectionId = "611cd1f63ddac"      // Replace with your product collection ID

    private val realtime by lazy { Realtime(client!!) }

    private val account by lazy { Account(client!!) }

    private val db by lazy { Database(client!!) }

    private val _productStream = MutableLiveData<Product>()
    val productStream: LiveData<Product> = _productStream

    private val _productDeleted = MutableLiveData<Product>()
    val productDeleted: LiveData<Product> = _productDeleted

    private var client: Client? = null

    var subscription: RealtimeSubscription? = null
        private set

    fun subscribeToProducts(context: Context) {
        buildClient(context)

        viewModelScope.launch {
            // Create a session so that we are authorized for realtime
            createSession()

            // Attach an error logger to our realtime instance
            realtime.doOnError { Log.e(this::class.java.name, it.message.toString()) }

            // Subscribe to document events for our collection and attach the handle product callback
            subscription = realtime.subscribe(
                "collections.${collectionId}.documents",
                payloadType = Product::class.java,
                callback = ::handleProductMessage
            )

            //createDummyProducts()
        }
    }

    private fun handleProductMessage(message: RealtimeResponseEvent<Product>) {
        // Because we set the `payloadType` to [`Product`](https://github.com/abnegate/android-realtime-starter/blob/main/app/src/main/java/io/appwrite/realtimestarter/Product.kt), `message.payload` is of type `Product`.
        when (message.event) {
            in
            "database.documents.create",
            "database.documents.update" -> {
                // The [`ProductAdapter`](https://github.com/abnegate/android-realtime-starter/blob/main/app/src/main/java/io/appwrite/realtimestarter/ProductAdapter.kt) will handle the diff for an update to an existing product.
                _productStream.postValue(message.payload!!)
            }
            "database.documents.delete" -> {
                _productDeleted.postValue(message.payload!!)
            }
        }
    }

    private suspend fun createDummyProducts() {
        // For testing; insert 100 products while subscribed
        val url = "https://dummyimage.com/600x400/cde/fff"
        for (i in 1 until 100) {
            db.createDocument(
                collectionId,
                Product("iPhone $i", "sku-$i", i.toDouble(), url).toJson(),
                listOf("*"),
                listOf("*")
            )
        }
    }

    private fun buildClient(context: Context) {
        if (client == null) {
            client = Client(context)
                .setEndpoint(endpoint)
                .setProject(projectId)
        }
    }

    private suspend fun createSession() {
        try {
            account.createAnonymousSession()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun closeSocket() {
        // Activity is being destroyed; close our socket connection if it's open
        subscription?.close()
    }
}