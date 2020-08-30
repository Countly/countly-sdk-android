package ly.count.android.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import ly.count.android.sdk.Countly
import ly.count.android.sdk.PersistentName

private const val ARG_COLOR = "color"

/**
 * A simple Fragment that fills itself with a single color. Used to demonstrate automatic fragment (view) tracking
 */
@PersistentName("ColorFragment")
class ColorFragment : Fragment() {

  var color: Int = 0
    private set

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    color = arguments?.getInt(ARG_COLOR) ?: ContextCompat.getColor(context!!, R.color.colorPrimary)
    // You can add segmentation to automatically recorded views!
    val viewSegmentation = mapOf(
        "Dogs" to 456,
        "Suns" to 1.12,
        "Elk" to "Reindeer"
    )
    // This segmentation will be included for this *instance* of this view's automatic recording!
    Countly.sharedInstance().views().addViewSegmentation(this, viewSegmentation)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(R.layout.fragment_color, container, false)
    view.findViewById<View>(R.id.root).setBackgroundColor(color)
    return view
  }

  companion object {

    /**
     * Creates a new instance of [ColorFragment]
     */
    @JvmStatic
    fun newInstance(color: Int) =
      ColorFragment().apply {
        arguments = Bundle().apply {
          putInt(ARG_COLOR, color)
        }
      }
  }
}
