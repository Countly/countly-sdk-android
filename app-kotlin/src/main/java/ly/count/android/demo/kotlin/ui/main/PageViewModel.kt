package ly.count.android.demo.kotlin.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class PageViewModel : ViewModel() {

  private val _index = MutableLiveData<Int>()
  val text: LiveData<String> = Transformations.map(_index) {
    "Hello world from section: $it"
  }

  fun setIndex(index: Int) {
    _index.value = index
  }
}