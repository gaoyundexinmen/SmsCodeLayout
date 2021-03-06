package com.gaoyun.smscodelayout.view

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.gaoyun.smscodelayout.R
import com.gaoyun.smscodelayout.interfaces.SmsCodeCompleteWatcher
import com.gaoyun.smscodelayout.interfaces.SmsCodeLengthWatcher
import com.gaoyun.smscodelayout.interfaces.SmsCodeTimeEmitter
import kotlinx.android.synthetic.main.view_sms_code.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates


class SmsCodeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    companion object {
        const val NORMAL_STYLE = 0
        const val BOLD_STYLE = 1
        const val ITALIC_STYLE = 2
        const val BOLD_ITALIC_STYLE = 3
    }

    private var codeLengthWatcher: SmsCodeLengthWatcher? = null
    private var codeCompleteWatcher: SmsCodeCompleteWatcher? = null

    var codeLength: Int by Delegates.observable(
        initialValue = 0,
        onChange = { _, _, newValue ->
            codeLengthWatcher?.codeLengthChanged(newValue)
        }
    )

    var codeComplete: Boolean by Delegates.observable(
        initialValue = false,
        onChange = { _, _, newValue ->
            codeCompleteWatcher?.codeCompleteChanged(newValue)
        }
    )

    @ObsoleteCoroutinesApi
    private var tickerChannel = ticker(delayMillis = 1_000, initialDelayMillis = 0)

    init {
        LayoutInflater.from(context).inflate(R.layout.view_sms_code, this, true)

        attrs?.let {
            val styledAttributes = context.obtainStyledAttributes(
                it,
                R.styleable.SmsCodeView, 0, 0
            )

            setTitleText(styledAttributes.getString(R.styleable.SmsCodeView_titleText))
            setTitleTextColor(
                styledAttributes.getColor(
                    R.styleable.SmsCodeView_titleTextColor,
                    ContextCompat.getColor(context, R.color.textColorPrimary)
                )
            )
            setTitleTextSize(
                styledAttributes.getDimension(R.styleable.SmsCodeView_titleTextSize, 14f)
            )
            setTitleTextStyle(
                styledAttributes.getInt(R.styleable.SmsCodeView_smsTitleTextStyle, NORMAL_STYLE)
            )

            setActionText(styledAttributes.getString(R.styleable.SmsCodeView_actionText))
            setActionTextColor(
                styledAttributes.getColor(
                    R.styleable.SmsCodeView_actionTextColor,
                    ContextCompat.getColor(context, R.color.colorBlue)
                )
            )
            setActionTextSize(
                styledAttributes.getDimension(R.styleable.SmsCodeView_actionTextSize, 14f)
            )
            setActionTextStyle(
                styledAttributes.getInt(R.styleable.SmsCodeView_smsActionTextStyle, NORMAL_STYLE)
            )

            setMechanic()

            styledAttributes.recycle()
        }
    }

    fun addCodeLengthWatcher(watcher: SmsCodeLengthWatcher) {
        codeLengthWatcher = watcher
    }

    fun addCodeCompleteWatcher(watcher: SmsCodeCompleteWatcher) {
        codeCompleteWatcher = watcher
    }

    fun getCode(): String {
        return "${txtNumber1.text}${txtNumber2.text}${txtNumber3.text}${txtNumber4.text}"
    }

    fun setCode(code: String) {
        if (code.length == 4) {
            setCodeFromString(code)
        }
    }

    fun setOnActionDoneClickListener(action: () -> Unit) {
        txtNumber4.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                action()
                true
            } else {
                false
            }
        }
    }

    fun setTitleText(value: String?) {
        value?.let {
            lblTitle.text = value
            lblTitle.visibility = View.VISIBLE
        }
    }

    fun setTitleTextColor(@ColorInt value: Int) {
        lblTitle.setTextColor(value)
    }

    fun setTitleTextSize(value: Float) {
        lblTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, value)
    }

    fun setTitleTextStyle(value: Int) {
        lblTitle.typeface = when (value) {
            NORMAL_STYLE -> Typeface.DEFAULT
            BOLD_STYLE -> Typeface.DEFAULT_BOLD
            ITALIC_STYLE -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            else -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        }
    }

    fun setActionText(value: String?) {
        value?.let {
            btnAction.text = value
            btnAction.visibility = View.VISIBLE
        }
    }

    fun setActionTextColor(@ColorInt value: Int) {
        btnAction.setTextColor(value)
    }

    fun setActionTextSize(value: Float) {
        btnAction.setTextSize(TypedValue.COMPLEX_UNIT_SP, value)
    }

    fun setActionTextStyle(value: Int) {
        btnAction.typeface = when (value) {
            NORMAL_STYLE -> Typeface.DEFAULT
            BOLD_STYLE -> Typeface.DEFAULT_BOLD
            ITALIC_STYLE -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            else -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        }
    }

    fun setOnActionClickListener(onClickListener: OnClickListener) {
        btnAction.setOnClickListener(onClickListener)
    }

    fun hideSoftKeyboardOnLastNumberInput(hide: Boolean, activity: Activity) {
        if (hide) {
            txtNumber4.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(code: Editable?) {
                    if (code?.length ?: 0 > 0) hideSoftKeyboard(activity)
                }

                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

            })
        }
    }

    private fun setCodeFromString(code: String) {
        txtNumber1.setText(code[0].toString())
        txtNumber2.setText(code[1].toString())
        txtNumber3.setText(code[2].toString())
        txtNumber4.setText(code[3].toString())
    }

    private fun hideSoftKeyboard(activity: Activity) {
        val focusedView = activity.currentFocus
        if (focusedView != null) {
            val windowToken = focusedView.windowToken
            if (windowToken != null) {
                val imm =
                    activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(windowToken, 0)
            }
        }
    }

    private fun showSoftKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, 0)
    }

    private fun updateWatchersValues() {
        codeLength = getCode().length
        codeComplete = getCode().length == 4
    }

    @ObsoleteCoroutinesApi
    fun addTimerToRepeatAction(time: Long, units: TimeUnit, timerEmitter: SmsCodeTimeEmitter) {
        val timer = units.toMillis(time)
        val startTime = System.currentTimeMillis()

        tickerChannel = ticker(delayMillis = 1_000, initialDelayMillis = 0)
        CoroutineScope(Dispatchers.Main).launch {
            for (event in tickerChannel) {
                val timeBeforeAction = timer - (System.currentTimeMillis() - startTime)
                if (timeBeforeAction < 1_000) {
                    tickerChannel.cancel()

                    timerEmitter.onTimerStop()
                    btnAction.isClickable = true
                } else {
                    val sdfMin = SimpleDateFormat("mm", Locale.getDefault())
                    val sdfSec = SimpleDateFormat("ss", Locale.getDefault())
                    val date = Date(timeBeforeAction)

                    timerEmitter.onTick(sdfMin.format(date).toInt(), sdfSec.format(date).toInt())
                    btnAction.isClickable = false
                }
            }
        }
    }

    @ObsoleteCoroutinesApi
    fun clearTimerToRepeatAction() {
        tickerChannel.cancel()
        btnAction.isClickable = true

    }

    private fun setMechanic() {
        txtNumber1.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    txtNumber1.apply {
                        isFocusable = true
                        requestFocus()
                        setSelection(txtNumber1.text.length)
                        showSoftKeyboard(this)
                    }
                }
                MotionEvent.ACTION_UP -> v.performClick()
                else -> {
                }
            }
            true
        }

        txtNumber2.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    txtNumber2.apply {
                        isFocusable = true
                        requestFocus()
                        setSelection(txtNumber2.text.length)
                        showSoftKeyboard(this)
                    }
                }
                MotionEvent.ACTION_UP -> v.performClick()
                else -> {
                }
            }
            true
        }

        txtNumber3.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    txtNumber3.apply {
                        isFocusable = true
                        requestFocus()
                        setSelection(txtNumber3.text.length)
                        showSoftKeyboard(this)
                    }
                }
                MotionEvent.ACTION_UP -> v.performClick()
                else -> {
                }
            }
            true
        }

        txtNumber4.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    txtNumber4.apply {
                        isFocusable = true
                        requestFocus()
                        setSelection(txtNumber4.text.length)
                        showSoftKeyboard(this)
                    }
                }
                MotionEvent.ACTION_UP -> v.performClick()
                else -> {
                }
            }
            true
        }

        txtNumber1.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(code: Editable?) {
                if (code != null) {
                    if (code.length == 4) {
                        setCodeFromString(code.toString())
                    } else if (txtNumber1.text.length == 1) {
                        txtNumber1.setSelection(txtNumber1.text.length)
                        if (code.isNotEmpty()) txtNumber2.requestFocus()
                    } else if (code.length > 4) {
                        setCode(code.toString().substring(0, 4))
                    } else if (code.isNotEmpty()) {
                        txtNumber1.setText(code.last().toString())
                    }
                }
                updateWatchersValues()
            }

            override fun beforeTextChanged(text: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(text: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

        })

        txtNumber2.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(code: Editable?) {
                if (code != null) {
                    if (code.length == 4) {
                        setCodeFromString(code.toString())
                    } else if (txtNumber2.text.length == 1) {
                        txtNumber2.setSelection(txtNumber2.text.length)
                        if (code.isNotEmpty()) txtNumber3.requestFocus()
                    } else if (code.length > 4) {
                        setCode(code.toString().substring(0, 4))
                    } else if (code.isNotEmpty()) {
                        txtNumber2.setText(code.last().toString())
                    }
                }
                updateWatchersValues()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

        })

        txtNumber3.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(code: Editable?) {
                if (code != null) {
                    if (code.length == 4) {
                        setCodeFromString(code.toString())
                    } else if (txtNumber3.text.length == 1) {
                        txtNumber3.setSelection(txtNumber3.text.length)
                        if (code.isNotEmpty()) txtNumber4.requestFocus()
                    } else if (code.length > 4) {
                        setCode(code.toString().substring(0, 4))
                    } else if (code.isNotEmpty()) {
                        txtNumber3.setText(code.last().toString())
                    }
                }
                updateWatchersValues()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

        })

        txtNumber4.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(code: Editable?) {
                if (code != null) {
                    when {
                        code.length == 4 -> {
                            setCodeFromString(code.toString())
                        }
                        txtNumber4.text.length == 1 -> {
                            txtNumber4.setSelection(txtNumber4.text.length)
                        }
                        code.length > 4 -> {
                            setCode(code.toString().substring(0, 4))
                        }
                        code.isNotEmpty() -> {
                            txtNumber4.setText(code.last().toString())
                        }
                    }
                }
                updateWatchersValues()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

        })

        txtNumber2.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                if (txtNumber2.text.isEmpty()) {
                    txtNumber1.setText("")
                    txtNumber1.requestFocus()
                }
            }
            false
        }

        txtNumber3.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                if (txtNumber3.text.isEmpty()) {
                    txtNumber2.setText("")
                    txtNumber2.requestFocus()
                }
            }
            false
        }

        txtNumber4.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                if (txtNumber4.text.isEmpty()) {
                    txtNumber3.setText("")
                    txtNumber3.requestFocus()
                }
            }
            false
        }

    }

}