package com.byteflow.www.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.byteflow.www.databinding.FragmentServersBinding
import com.byteflow.www.ui.adapters.ServerAdapter
import com.byteflow.www.data.models.Server

class ServersFragment : Fragment() {
    private var _binding: FragmentServersBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var serverAdapter: ServerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadServers()
    }

    private fun setupRecyclerView() {
        serverAdapter = ServerAdapter { server ->
            // Handle server selection
            onServerSelected(server)
        }
        
        binding.serversRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = serverAdapter
        }
    }

    private fun loadServers() {
        val servers = listOf(
            Server("HK01", "香港-01", "香港", "12ms", true, 95),
            Server("HK02", "香港-02", "香港", "15ms", false, 88),
            Server("US01", "美国-洛杉矶", "美国", "180ms", false, 92),
            Server("JP01", "日本-东京", "日本", "65ms", false, 98),
            Server("SG01", "新加坡-01", "新加坡", "45ms", false, 85),
            Server("DE01", "德国-法兰克福", "德国", "220ms", false, 91)
        )
        
        serverAdapter.submitList(servers)
    }

    private fun onServerSelected(server: Server) {
        // Update selected server
        val currentList = serverAdapter.currentList.map { 
            it.copy(isSelected = it.id == server.id)
        }
        serverAdapter.submitList(currentList)
        
        // TODO: Update current server in preferences/data layer
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}