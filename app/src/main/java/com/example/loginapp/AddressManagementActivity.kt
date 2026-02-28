package com.example.loginapp

import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.location.Geocoder
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.loginapp.adapters.SavedAddressAdapter
import com.example.loginapp.databinding.ActivityAddressManagementBinding
import com.example.loginapp.models.SavedAddress
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import android.app.Activity
import android.content.Intent

/**
 * Activity para gerenciar endereços salvos do cliente
 */
class AddressManagementActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAddressManagementBinding
    private lateinit var addressAdapter: SavedAddressAdapter
    private var savedAddresses = mutableListOf<SavedAddress>()
    private var pendingCoordinates: GeoPoint? = null
    private var currentDialogBinding: com.example.loginapp.databinding.DialogAddAddressBinding? = null
    
    companion object {
        private const val MAP_PICKER_REQUEST = 2001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddressManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Google Play Services não é mais obrigatório
        // O app funciona sem Google Maps/Localização
        
        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        loadSavedAddresses()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Meus Endereços"
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        addressAdapter = SavedAddressAdapter(
            addresses = savedAddresses,
            onAddressClick = { address ->
                showAddressDetails(address)
            },
            onEditClick = { address ->
                editAddress(address)
            },
            onDeleteClick = { address ->
                deleteAddress(address)
            },
            onSetDefaultClick = { address ->
                setDefaultAddress(address)
            }
        )
        
        binding.recyclerViewAddresses.apply {
            layoutManager = LinearLayoutManager(this@AddressManagementActivity)
            adapter = addressAdapter
        }
    }
    
    private fun setupClickListeners() {
        binding.fabAddAddress.setOnClickListener {
            showAddAddressDialog()
        }
    }
    
    /**
     * Configura o spinner de estados no diálogo
     */
    private fun setupStateSpinner(dialogBinding: com.example.loginapp.databinding.DialogAddAddressBinding) {
        val states = com.example.loginapp.utils.BrazilianStates.getFormattedStates()
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, states)
        dialogBinding.spinnerState.setAdapter(adapter)
        dialogBinding.spinnerState.keyListener = null
        
        // Listener para quando um estado for selecionado
        dialogBinding.spinnerState.setOnItemClickListener { _, _, position, _ ->
            val selectedState = states[position]
            android.util.Log.d("AddressManagement", "Estado selecionado: $selectedState")
        }
        
        // Mostrar o dropdown imediatamente no primeiro toque/foco
        dialogBinding.spinnerState.threshold = 1
        dialogBinding.spinnerState.setOnClickListener {
            dialogBinding.spinnerState.showDropDown()
        }
        dialogBinding.spinnerState.setOnTouchListener { view, _ ->
            view.performClick()
            dialogBinding.spinnerState.showDropDown()
            false
        }
        
        // Configurar para mostrar dropdown quando ganhar foco
        dialogBinding.spinnerState.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                dialogBinding.spinnerState.showDropDown()
            }
        }
    }

    private fun setupCepFormatting(editText: com.google.android.material.textfield.TextInputEditText) {
        editText.addTextChangedListener(object : android.text.TextWatcher {
            private var isUpdating = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUpdating) return
                val digits = s.toString().replace(Regex("[^\\d]"), "")
                val formatted = when {
                    digits.length <= 5 -> digits
                    else -> "${digits.substring(0, 5)}-${digits.substring(5, minOf(digits.length, 8))}"
                }
                isUpdating = true
                editText.setText(formatted)
                editText.setSelection(formatted.length)
                isUpdating = false
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun showAddAddressDialog(address: String = "", coordinates: GeoPoint? = null) {
        val dialogBinding = com.example.loginapp.databinding.DialogAddAddressBinding.inflate(layoutInflater)
        currentDialogBinding = dialogBinding
        pendingCoordinates = coordinates

        // Configurar spinner de estados
        setupStateSpinner(dialogBinding)

        // Configurar máscara de CEP
        setupCepFormatting(dialogBinding.etZipCode)
        
        // Preencher campos se fornecidos
        if (address.isNotEmpty()) {
            dialogBinding.etAddress.setText(address)
        }
        
        updateSelectedCoordsLabel(dialogBinding, pendingCoordinates)
        dialogBinding.btnPickOnMap.setOnClickListener {
            openMapPicker()
        }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Adicionar Endereço")
            .setView(dialogBinding.root)
            .setPositiveButton("Salvar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (pendingCoordinates == null) {
                showToast("Selecione a localização no mapa antes de salvar")
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val name = dialogBinding.etName.text.toString().trim()
                val fullAddress = dialogBinding.etAddress.text.toString().trim()
                val complement = dialogBinding.etComplement.text.toString().trim()
                val neighborhood = dialogBinding.etNeighborhood.text.toString().trim()
                val city = dialogBinding.etCity.text.toString().trim()
                val stateText = dialogBinding.spinnerState.text.toString().trim()
                val zipCode = dialogBinding.etZipCode.text.toString().trim()
                val isDefault = dialogBinding.cbDefault.isChecked

                if (name.isEmpty() || fullAddress.isEmpty()) {
                    showToast("Nome e endereço são obrigatórios")
                    return@launch
                }

                val state = if (stateText.contains(" - ")) stateText.substring(0, 2) else stateText

                val newAddress = SavedAddress(
                    name = name,
                    address = fullAddress,
                    complement = complement,
                    neighborhood = neighborhood,
                    city = city,
                    state = state,
                    zipCode = zipCode,
                    coordinates = pendingCoordinates,
                    isDefault = isDefault
                )

                saveAddress(newAddress)
                dialog.dismiss()
            }
        }
    }
    
    private fun showAddressDetails(address: SavedAddress) {
        val message = buildString {
            append("Nome: ${address.name}\n")
            append("Endereço: ${address.getFullAddress()}\n")
            if (address.isDefault) {
                append("\n⭐ Endereço padrão")
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Detalhes do Endereço")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun editAddress(address: SavedAddress) {
        val dialogBinding = com.example.loginapp.databinding.DialogAddAddressBinding.inflate(layoutInflater)
        currentDialogBinding = dialogBinding
        pendingCoordinates = address.coordinates

        // Configurar máscara de CEP
        setupCepFormatting(dialogBinding.etZipCode)

        // Preencher campos com dados existentes
        dialogBinding.etName.setText(address.name)
        dialogBinding.etAddress.setText(address.address)
        dialogBinding.etComplement.setText(address.complement)
        dialogBinding.etNeighborhood.setText(address.neighborhood)
        dialogBinding.etCity.setText(address.city)
        dialogBinding.spinnerState.setText(address.state)
        dialogBinding.etZipCode.setText(address.zipCode)
        dialogBinding.cbDefault.isChecked = address.isDefault
        updateSelectedCoordsLabel(dialogBinding, pendingCoordinates)
        dialogBinding.btnPickOnMap.setOnClickListener {
            openMapPicker()
        }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Editar Endereço")
            .setView(dialogBinding.root)
            .setPositiveButton("Salvar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (pendingCoordinates == null) {
                showToast("Selecione a localização no mapa antes de salvar")
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val name = dialogBinding.etName.text.toString().trim()
                val fullAddress = dialogBinding.etAddress.text.toString().trim()
                val complement = dialogBinding.etComplement.text.toString().trim()
                val neighborhood = dialogBinding.etNeighborhood.text.toString().trim()
                val city = dialogBinding.etCity.text.toString().trim()
                val state = dialogBinding.spinnerState.text.toString().trim()
                val zipCode = dialogBinding.etZipCode.text.toString().trim()
                val isDefault = dialogBinding.cbDefault.isChecked

                if (name.isEmpty() || fullAddress.isEmpty()) {
                    showToast("Nome e endereço são obrigatórios")
                    return@launch
                }

                val updatedAddress = address.copy(
                    name = name,
                    address = fullAddress,
                    complement = complement,
                    neighborhood = neighborhood,
                    city = city,
                    state = state,
                    zipCode = zipCode,
                    coordinates = pendingCoordinates,
                    isDefault = isDefault
                )

                updateAddress(updatedAddress)
                dialog.dismiss()
            }
        }
    }
    
    private fun deleteAddress(address: SavedAddress) {
        AlertDialog.Builder(this)
            .setTitle("Remover Endereço")
            .setMessage("Tem certeza que deseja remover o endereço \"${address.name}\"?")
            .setPositiveButton("Remover") { _, _ ->
                lifecycleScope.launch {
                    val result = FirebaseAddressManager().deleteAddress(address.id)
                    if (result.isSuccess) {
                        showToast("Endereço removido com sucesso")
                        loadSavedAddresses()
                    } else {
                        showToast("Erro ao remover endereço: ${result.exceptionOrNull()?.message}")
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun setDefaultAddress(address: SavedAddress) {
        lifecycleScope.launch {
            val result = FirebaseAddressManager().setDefaultAddress(address.id)
            if (result.isSuccess) {
                showToast("Endereço definido como padrão")
                loadSavedAddresses()
            } else {
                showToast("Erro ao definir endereço padrão: ${result.exceptionOrNull()?.message}")
            }
        }
    }
    
    private fun saveAddress(address: SavedAddress) {
        lifecycleScope.launch {
            val result = FirebaseAddressManager().saveAddress(address)
            if (result.isSuccess) {
                showToast("Endereço salvo com sucesso")
                loadSavedAddresses()
            } else {
                showToast("Erro ao salvar endereço: ${result.exceptionOrNull()?.message}")
            }
        }
    }
    
    private fun updateAddress(address: SavedAddress) {
        lifecycleScope.launch {
            val result = FirebaseAddressManager().updateAddress(address)
            if (result.isSuccess) {
                showToast("Endereço atualizado com sucesso")
                loadSavedAddresses()
            } else {
                showToast("Erro ao atualizar endereço: ${result.exceptionOrNull()?.message}")
            }
        }
    }
    
    private fun loadSavedAddresses() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            
            val result = FirebaseAddressManager().getUserAddresses()
            if (result.isSuccess) {
                savedAddresses.clear()
                savedAddresses.addAll(result.getOrNull() ?: emptyList())
                addressAdapter.notifyDataSetChanged()
                
                if (savedAddresses.isEmpty()) {
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.recyclerViewAddresses.visibility = View.GONE
                } else {
                    binding.layoutEmpty.visibility = View.GONE
                    binding.recyclerViewAddresses.visibility = View.VISIBLE
                }
            } else {
                showToast("Erro ao carregar endereços: ${result.exceptionOrNull()?.message}")
            }
            
            binding.progressBar.visibility = View.GONE
        }
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun openMapPicker() {
        val intent = Intent(this, AddressMapPickerActivity::class.java)
        startActivityForResult(intent, MAP_PICKER_REQUEST)
    }
    
    private fun updateSelectedCoordsLabel(dialogBinding: com.example.loginapp.databinding.DialogAddAddressBinding?, coords: GeoPoint?) {
        dialogBinding ?: return
        dialogBinding.tvSelectedCoords.text = coords?.let {
            "Lat: %.5f, Lng: %.5f".format(it.latitude, it.longitude)
        } ?: "Selecione a localização no mapa (obrigatório)"
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MAP_PICKER_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val lat = data.getDoubleExtra(AddressMapPickerActivity.EXTRA_LAT, Double.NaN)
            val lng = data.getDoubleExtra(AddressMapPickerActivity.EXTRA_LNG, Double.NaN)
            if (!lat.isNaN() && !lng.isNaN()) {
                pendingCoordinates = GeoPoint(lat, lng)
                updateSelectedCoordsLabel(currentDialogBinding, pendingCoordinates)
            }
        }
    }
    
    /**
     * Geocodifica um endereço textual em coordenadas (GeoPoint).
     * Usa Geocoder local; salva null se não encontrar.
     */
    private suspend fun geocodeAddress(query: String): GeoPoint? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(this@AddressManagementActivity, Locale("pt", "BR"))
            val results = geocoder.getFromLocationName(query, 1)
            if (!results.isNullOrEmpty()) {
                val loc = results.first()
                return@withContext GeoPoint(loc.latitude, loc.longitude)
            }
            null
        } catch (e: Exception) {
            android.util.Log.e("AddressManagement", "Geocode falhou: ${e.message}")
            null
        }
    }
    
    /**
     * Verifica se o Google Play Services está disponível
     * Função desabilitada - não é mais necessária
     */
    /*
    private fun isGooglePlayServicesAvailable(): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
        return resultCode == ConnectionResult.SUCCESS
    }
    */
}

