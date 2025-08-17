interface TransferListener {
    fun onProgress(progress: Int)
    fun onSpeedUpdate(kbps: Float)
    fun onError(e: Exception)
}