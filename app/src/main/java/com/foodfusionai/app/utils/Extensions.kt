package com.foodfusionai.app.utils

import android.content.Context
import android.util.Patterns
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import coil.load
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Extension functions for various classes.
 */

fun View.show() {
    visibility = View.VISIBLE
}

fun View.hide() {
    visibility = View.GONE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun View.setOnSafeClickListener(interval: Long = 600, action: (View) -> Unit) {
    setOnClickListener(SafeClickListener(interval, action))
}

fun String.isValidEmail(): Boolean {
    val emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    return this.isNotEmpty() && this.matches(emailRegex.toRegex())
}

fun String.isValidPassword(): Boolean {
    if (this.length < Constants.MIN_PASSWORD_LENGTH) return false
    val hasUpper = this.any { it.isUpperCase() }
    val hasLower = this.any { it.isLowerCase() }
    val hasDigit = this.any { it.isDigit() }
    val hasSpecial = this.any { !it.isLetterOrDigit() }
    return hasUpper && hasLower && hasDigit && hasSpecial
}

fun String.isValidPhone(): Boolean {
    return this.length == Constants.PHONE_NUMBER_LENGTH && this.all { it.isDigit() }
}

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Fragment.hideKeyboard() {
    view?.let { activity?.hideKeyboard(it) }
}

fun Context.hideKeyboard(view: View) {
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}

fun EditText.trimmedText(): String {
    return this.text.toString().trim()
}

fun Long.toFormattedDate(pattern: String = "dd MMM yyyy"): String {
    val sdf = SimpleDateFormat(pattern, Locale.getDefault())
    return sdf.format(Date(this))
}

fun Double.toCurrencyFormat(): String {
    return String.format("₹%.2f", this)
}

fun ImageView.loadImage(url: String) {
    this.load(url) {
        crossfade(true)
    }
}

fun RecyclerView.addDivider() {
    val dividerItemDecoration = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
    addItemDecoration(dividerItemDecoration)
}
