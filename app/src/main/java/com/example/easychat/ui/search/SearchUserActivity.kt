package com.example.easychat.ui.search

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.easychat.databinding.ActivitySearchUserBinding
import com.example.easychat.model.UserModel

class SearchUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchUserBinding
    private lateinit var adapter: SearchUserRecyclerAdapter
    private val viewModel: SearchViewModel by viewModels()

    private val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) importContacts()
        else android.widget.Toast.makeText(this, "Permissão de contatos negada", android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        binding.seachUsernameInput.requestFocus()
    }

    private fun setupRecyclerView() {
        adapter = SearchUserRecyclerAdapter(applicationContext) { selectedUsers ->
            updateGroupSelectionBanner(selectedUsers)
        }
        binding.searchUserRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.searchUserRecyclerView.adapter = adapter
    }

    private fun setupListeners() {
        binding.backBtn.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.searchUserBtn.setOnClickListener {
            val term = binding.seachUsernameInput.text.toString().trim()
            if (term.length < 3) {
                binding.seachUsernameInput.error = "Digite ao menos 3 caracteres"
                return@setOnClickListener
            }
            adapter.selectionMode = false
            viewModel.searchUsers(term)
        }

        // Botão de criar grupo — alterna modo de seleção
        binding.createGroupBtn.setOnClickListener {
            if (adapter.selectionMode) {
                // Cancela seleção
                adapter.selectionMode = false
                binding.groupSelectionBanner.visibility = View.GONE
            } else {
                if (adapter.currentList.isEmpty()) {
                    android.widget.Toast.makeText(this, "Busque usuários antes de criar um grupo", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                adapter.selectionMode = true
                binding.groupSelectionBanner.visibility = View.VISIBLE
                updateGroupSelectionBanner(emptyList())
            }
        }

        // Confirma criação do grupo
        binding.confirmGroupBtn.setOnClickListener {
            val selected = adapter.getSelectedUsers()
            if (selected.isEmpty()) {
                android.widget.Toast.makeText(this, "Selecione ao menos um participante", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showGroupNameDialog(selected)
        }

        // Botão de importar contatos (long press no search_user_btn)
        binding.searchUserBtn.setOnLongClickListener {
            requestContactsAndImport()
            true
        }
    }

    private fun updateGroupSelectionBanner(selected: List<UserModel>) {
        binding.groupSelectedCount.text = "${selected.size} selecionado(s)"
        binding.confirmGroupBtn.isEnabled = selected.isNotEmpty()
        binding.confirmGroupBtn.alpha = if (selected.isNotEmpty()) 1f else 0.5f
    }

    private fun showGroupNameDialog(members: List<UserModel>) {
        val input = EditText(this).apply {
            hint = "Nome do grupo"
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("Novo Grupo")
            .setMessage("${members.size} participante(s) selecionado(s)")
            .setView(input)
            .setPositiveButton("Criar") { _, _ ->
                val name = input.text.toString().trim()
                if (name.length < 2) {
                    android.widget.Toast.makeText(this, "Nome deve ter ao menos 2 caracteres", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.createGroup(name, members)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun requestContactsAndImport() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) {
            importContacts()
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun importContacts() {
        val phones = mutableListOf<String>()
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )
        cursor?.use {
            val colIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val raw = it.getString(colIdx) ?: continue
                // Normaliza: remove espaços, traços, parênteses
                val normalized = raw.replace(Regex("[^+\\d]"), "")
                if (normalized.length >= 8) phones.add(normalized)
            }
        }
        if (phones.isEmpty()) {
            android.widget.Toast.makeText(this, "Nenhum contato encontrado", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        android.widget.Toast.makeText(this, "Buscando ${phones.size} contatos...", android.widget.Toast.LENGTH_SHORT).show()
        viewModel.searchByPhones(phones.distinct())
    }

    private fun observeViewModel() {
        viewModel.results.observe(this) { users ->
            adapter.submitList(users)
        }

        viewModel.loading.observe(this) { isLoading ->
            binding.searchUserBtn.isEnabled = !isLoading
            binding.searchUserBtn.alpha = if (isLoading) 0.5f else 1.0f
        }

        viewModel.groupCreated.observe(this) { room ->
            room ?: return@observe
            android.widget.Toast.makeText(this, "Grupo \"${room.name}\" criado!", android.widget.Toast.LENGTH_SHORT).show()
            adapter.selectionMode = false
            binding.groupSelectionBanner.visibility = View.GONE
            viewModel.clearGroupCreated()
            finish()
        }

        viewModel.error.observe(this) { err ->
            err ?: return@observe
            android.widget.Toast.makeText(this, err, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }
}
