package com.greenbits.bugsnag_flutter

import androidx.annotation.NonNull
import android.content.Context
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.Configuration
import com.bugsnag.android.Event
import com.bugsnag.android.BreadcrumbType

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar

/** BugsnagFlutterPlugin */
class BugsnagFlutterPlugin: FlutterPlugin, MethodCallHandler {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private lateinit var context : Context
  private var configured = false

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.getApplicationContext()
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "plugins.greenbits.com/bugsnag_flutter")
    channel.setMethodCallHandler(this)
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when {
      call.method == "configure" -> {
        val apiKey = call.argument<String>("androidApiKey")
        val releaseStage = call.argument<String>("releaseStage")

        val config: Configuration = try {
          Configuration.load(context)
        } catch (e: Throwable) {
          if (apiKey.isNullOrBlank()) {
            result.success(false)
            return
          }
          Configuration(apiKey)
        }

        if (!apiKey.isNullOrBlank()) {
          config.apiKey = apiKey
        }

        if (releaseStage != null) {
          config.releaseStage = releaseStage!!
        }

        Bugsnag.start(context, config)
        configured = true
        result.success(true)
      }
      call.method == "notify" -> {
        if (configured) {
          val name = call.argument<String>("name")!!
          val description = call.argument<String>("description")!!
          val fullOutput = call.argument<String>("fullOutput")!!
          val context = call.argument<String>("context")!!
          val additionalStackTrace = call.argument<String>("additionalStackTrace")

          val stackTrace = call.argument<List<HashMap<String, Any?>>>("stackTrace")!!

          val throwable = Throwable(name)
          val flutterStackTrace = ArrayList<StackTraceElement>()
          for (flutterFrame in stackTrace) {
            flutterStackTrace.add(StackTraceElement("", flutterFrame["method"] as? String, flutterFrame["file"] as? String, flutterFrame["lineNumber"] as? Int ?: 0))
          }
          throwable.stackTrace = flutterStackTrace.toTypedArray()

          Bugsnag.notify(throwable, { event: Event ->
            event.addMetadata("Flutter", "Context", context)
            event.addMetadata("Flutter", "Full Error", fullOutput)
            if (additionalStackTrace != null) {
              event.addMetadata("Flutter", "StackTrace", additionalStackTrace);
            }

            event.errors[0].errorClass = name

            for (frame in event.errors[0].stacktrace) {
              val index = event.errors[0].stacktrace.indexOf(frame)

              val flutterFrame = stackTrace[index]
              frame.inProject = flutterFrame["inProject"] as? Boolean
              frame.columnNumber = flutterFrame["columnNumber"] as? Int
            }
            true
          })
        }
        result.success(true)
      }
      call.method == "setUser" -> {
        if (configured) {
          val id = call.argument<String>("id")!!
          val email = call.argument<String>("email")!!
          val name = call.argument<String>("name")!!
          Bugsnag.setUser(id, email, name)
        }
        result.success(true)
      }
      call.method == "leaveBreadcrumb" -> {
        if (configured) {
          val message = call.argument<String>("message")!!
          val typeIndex = call.argument<Int>("type")!!
          val type = BreadcrumbType.values()[typeIndex]

          Bugsnag.leaveBreadcrumb(message, emptyMap<String, Object>(), type)
        }
        result.success(true)
      }
      else -> result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }
}
