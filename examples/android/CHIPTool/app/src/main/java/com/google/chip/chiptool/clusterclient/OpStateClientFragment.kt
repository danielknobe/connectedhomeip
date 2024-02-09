package com.google.chip.chiptool.clusterclient

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.ClusterIDMapping.*
import chip.devicecontroller.InvokeCallback
import chip.devicecontroller.ReportCallback
import chip.devicecontroller.ResubscriptionAttemptCallback
import chip.devicecontroller.SubscriptionEstablishedCallback
import chip.devicecontroller.model.ChipAttributePath
import chip.devicecontroller.model.ChipEventPath
import chip.devicecontroller.model.InvokeElement
import chip.devicecontroller.model.NodeState
import com.google.chip.chiptool.ChipClient
import com.google.chip.chiptool.GenericChipDeviceListener
import com.google.chip.chiptool.R
import com.google.chip.chiptool.databinding.OpStateClientFragmentBinding
import com.google.chip.chiptool.util.toAny
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import matter.tlv.AnonymousTag
import matter.tlv.ContextSpecificTag
import matter.tlv.TlvReader
import matter.tlv.TlvWriter

class OpStateClientFragment : Fragment() {
  private val deviceController: ChipDeviceController
    get() = ChipClient.getDeviceController(requireContext())

  private lateinit var scope: CoroutineScope

  private lateinit var addressUpdateFragment: AddressUpdateFragment

  private var _binding: OpStateClientFragmentBinding? = null
  private val binding
    get() = _binding!!

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = OpStateClientFragmentBinding.inflate(inflater, container, false)
    scope = viewLifecycleOwner.lifecycleScope

    deviceController.setCompletionListener(ChipControllerCallback())

    addressUpdateFragment =
      childFragmentManager.findFragmentById(R.id.addressUpdateFragment) as AddressUpdateFragment

    binding.pauseBtn.setOnClickListener {
      scope.launch { sendOperationalStateClusterCommand(OperationalState.Command.Pause) }
    }
    binding.stopBtn.setOnClickListener {
      scope.launch { sendOperationalStateClusterCommand(OperationalState.Command.Stop) }
    }
    binding.startBtn.setOnClickListener {
      scope.launch { sendOperationalStateClusterCommand(OperationalState.Command.Start) }
    }
    binding.resumeBtn.setOnClickListener {
      scope.launch { sendOperationalStateClusterCommand(OperationalState.Command.Resume) }
    }

    binding.readOpStateBtn.setOnClickListener { scope.launch { sendReadOpStateClick() } }

    return binding.root
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  private suspend fun sendReadOpStateClick() {
    val endpointId = addressUpdateFragment.endpointId
    val clusterId = OperationalState.ID
    val attributeId = OperationalState.Attribute.OperationalState.id

    val attributePath = ChipAttributePath.newInstance(endpointId, clusterId, attributeId)

    ChipClient.getDeviceController(requireContext())
      .readPath(
        object : ReportCallback {
          override fun onError(
            attributePath: ChipAttributePath?,
            eventPath: ChipEventPath?,
            ex: java.lang.Exception
          ) {
            Log.e(TAG, "Error reading operationalState attribute", ex)
          }

          override fun onReport(nodeState: NodeState?) {
            val tlv =
              nodeState
                ?.getEndpointState(endpointId)
                ?.getClusterState(clusterId)
                ?.getAttributeState(attributeId)
                ?.tlv
            val value = tlv?.let { TlvReader(it).toAny() }
            Log.v(TAG, "OperationalState attribute value: $value")
            showMessage("OperationalState attribute value: $value")
          }
        },
        getConnectedDevicePointer(),
        listOf(attributePath),
        null,
        false,
        0 /* imTimeoutMs */
      )
  }

  inner class ChipControllerCallback : GenericChipDeviceListener() {
    override fun onConnectDeviceComplete() {}

    override fun onCommissioningComplete(nodeId: Long, errorCode: Int) {
      Log.d(TAG, "onCommissioningComplete for nodeId $nodeId: $errorCode")
      showMessage("Address update complete for nodeId $nodeId with code $errorCode")
    }

    override fun onNotifyChipConnectionClosed() {
      Log.d(TAG, "onNotifyChipConnectionClosed")
    }

    override fun onCloseBleComplete() {
      Log.d(TAG, "onCloseBleComplete")
    }

    override fun onError(error: Throwable?) {
      Log.d(TAG, "onError: $error")
    }
  }

  private suspend fun sendOperationalStateClusterCommand(commandId: OperationalState.Command) {
    // TODO : Need to be implement poj-to-tlv
    val tlvWriter = TlvWriter()
    tlvWriter.startStructure(AnonymousTag)
    tlvWriter.endStructure()
    val invokeElement =
      InvokeElement.newInstance(
        addressUpdateFragment.endpointId,
        OperationalState.ID,
        commandId.id,
        tlvWriter.getEncoded(),
        null
      )

    deviceController.invoke(
      object : InvokeCallback {
        override fun onError(ex: Exception?) {
          showMessage("${commandId.name} command failure $ex")
          Log.e(TAG, "${commandId.name} command failure", ex)
        }

        override fun onResponse(invokeElement: InvokeElement?, successCode: Long) {
          Log.e(TAG, "onResponse : $invokeElement, Code : $successCode")
          showMessage("${commandId.name} command success")
        }
      },
      getConnectedDevicePointer(),
      invokeElement,
      0,
      0
    )
  }

  private suspend fun getConnectedDevicePointer(): Long {
    return ChipClient.getConnectedDevicePointer(requireContext(), addressUpdateFragment.deviceId)
  }

  private fun showMessage(msg: String) {
    requireActivity().runOnUiThread { binding.commandStatusTv.text = msg }
  }

  override fun onResume() {
    super.onResume()
    addressUpdateFragment.endpointId = OPERATIONAL_STATE_CLUSTER_ENDPOINT
  }

  companion object {
    private const val TAG = "OpStateClientFragment"

    private const val OPERATIONAL_STATE_CLUSTER_ENDPOINT = 1

    fun newInstance(): OpStateClientFragment = OpStateClientFragment()
  }
}
