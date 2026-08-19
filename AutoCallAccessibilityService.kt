package com.example.autocall5

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutoCallAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var running = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var state = State.WAITING
    private var actionLock = false
    private var callStartedAt = 0L

    // Tùy chọn: nếu app giao hàng có package riêng, điền package vào đây để an toàn hơn.
    // Ví dụ: private val deliveryPackage = "com.example.delivery"
    private val deliveryPackage: String? = null

    private enum class State {
        WAITING, CALL_MENU, DIALER, IN_CALL, RETURNING
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = false
        state = State.WAITING
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!running || event == null || actionLock) return

        val root = rootInActiveWindow ?: return
        val pkg = event.packageName?.toString() ?: ""

        if (deliveryPackage != null && pkg != deliveryPackage &&
            state != State.DIALER && state != State.IN_CALL) return

        when (state) {
            State.WAITING -> {
                // Tìm nút Gọi trên đơn hàng.
                val call = findClickableByText(root, listOf("Gọi"))
                if (call != null && !isCallMenu(root)) {
                    state = State.CALL_MENU
                    clickDelayed(call, 500)
                }
            }

            State.CALL_MENU -> {
                val receiver = findClickableByText(root, listOf("Gọi người nhận"))
                if (receiver != null) {
                    state = State.DIALER
                    clickDelayed(receiver, 600)
                }
            }

            State.DIALER -> {
                // Chờ trình quay số; thêm 5 vào cuối ô số.
                val numberField = findEditable(root)
                if (numberField != null) {
                    actionLock = true
                    handler.postDelayed({
                        appendFive(numberField)
                        handler.postDelayed({
                            val callButton = findCallButton(rootInActiveWindow)
                            if (callButton != null) {
                                callButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                callStartedAt = System.currentTimeMillis()
                                state = State.IN_CALL
                                handler.postDelayed({ hangUpAndReturn() }, 12_000)
                            } else {
                                actionLock = false
                            }
                        }, 700)
                    }, 700)
                }
            }

            State.IN_CALL -> {
                // Timer 12 giây xử lý việc tắt máy.
            }

            State.RETURNING -> {
                returnToNextOrder()
            }
        }
    }

    private fun hangUpAndReturn() {
        if (!running) {
            actionLock = false
            return
        }

        val root = rootInActiveWindow
        val hang = findClickableByText(
            root,
            listOf("Tắt", "Kết thúc", "Kết thúc cuộc gọi", "End call")
        )

        if (hang != null) {
            hang.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            // Nhiều trình quay số có nút kết thúc không có text.
            // Thử GLOBAL_ACTION_BACK để rời màn hình cuộc gọi.
            performGlobalAction(GLOBAL_ACTION_BACK)
        }

        state = State.RETURNING
        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed({
                swipeUp()
                state = State.WAITING
                actionLock = false
            }, 1200)
        }, 1200)
    }

    private fun returnToNextOrder() {
        actionLock = false
        swipeUp()
        state = State.WAITING
    }

    private fun appendFive(node: AccessibilityNodeInfo) {
        try {
            val old = node.text?.toString() ?: ""
            val bundle = android.os.Bundle()
            bundle.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                old + "5"
            )
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
        } catch (_: Exception) {
        }
    }

    private fun findEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val list = ArrayList<AccessibilityNodeInfo>()
        collect(root, list)
        return list.firstOrNull {
            it.isEditable && it.isVisibleToUser
        }
    }

    private fun findCallButton(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val list = ArrayList<AccessibilityNodeInfo>()
        collect(root, list)
        val names = listOf("Gọi", "Call", "Dial", "Gọi điện")
        return list.firstOrNull { n ->
            val t = (n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")
            n.isVisibleToUser && names.any { t.contains(it, ignoreCase = true) }
        }
    }

    private fun findClickableByText(
        root: AccessibilityNodeInfo?,
        texts: List<String>
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        val list = ArrayList<AccessibilityNodeInfo>()
        collect(root, list)
        return list.firstOrNull { n ->
            if (!n.isVisibleToUser) return@firstOrNull false
            val t = (n.text?.toString() ?: "") + " " +
                    (n.contentDescription?.toString() ?: "")
            n.isClickable && texts.any { t.trim().equals(it, ignoreCase = true) }
        }
    }

    private fun isCallMenu(root: AccessibilityNodeInfo): Boolean {
        return findClickableByText(root, listOf("Gọi người nhận")) != null
    }

    private fun collect(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        out.add(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collect(it, out) }
        }
    }

    private fun clickDelayed(node: AccessibilityNodeInfo, delay: Long) {
        actionLock = true
        handler.postDelayed({
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            actionLock = false
        }, delay)
    }

    private fun swipeUp() {
        val dm = resources.displayMetrics
        val x = dm.widthPixels * 0.5f
        val startY = dm.heightPixels * 0.78f
        val endY = dm.heightPixels * 0.35f

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 500))
            .build()
        dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() {
        running = false
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
