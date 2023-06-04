package com.bangkit.caviar.dialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import com.bangkit.caviar.databinding.LayoutDialogBinding

class DialogResult(context: Context) {
    private val dialog: Dialog = Dialog(context)
    private val binding: LayoutDialogBinding

    init {
        binding = LayoutDialogBinding.inflate(LayoutInflater.from(context), null, false)
        dialog.setContentView(binding.root)

        val layoutParams = WindowManager.LayoutParams()
        layoutParams.copyFrom(dialog.window?.attributes)
        layoutParams.horizontalMargin = 100f // Adjust the margin value as per your requirements
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        dialog.window?.attributes = layoutParams

        val colorDrawble = ColorDrawable(Color.TRANSPARENT)
        val insetDrawable = InsetDrawable(colorDrawble, 40)
        dialog.window?.setBackgroundDrawable(insetDrawable)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
    }

    fun setNegativeButton(text: String, onClickListener: () -> Unit) {
        binding.btnNegative.text = text
        binding.btnNegative.visibility = android.view.View.VISIBLE
        binding.btnNegative.setOnClickListener {
            onClickListener()
        }
    }

    fun setPositiveButton(text: String, onClickListener: () -> Unit) {
        binding.btnPositive.text = text
        binding.btnPositive.visibility = android.view.View.VISIBLE
        binding.btnPositive.setOnClickListener {
            onClickListener()
        }
    }

    fun setTitle(title: String) {
        binding.dialogTitle.text = title
    }

    fun setImage(image: Int) {
        binding.dialogImage.setImageResource(image)
    }

    fun setMessage(message: String) {
        binding.dialogText.text = message
    }

    fun show() {
        dialog.show()
    }

    fun dismiss() {
        dialog.dismiss()
    }
}
