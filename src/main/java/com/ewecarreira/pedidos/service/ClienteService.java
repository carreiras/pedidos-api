package com.ewecarreira.pedidos.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import com.ewecarreira.pedidos.domain.entity.Cidade;
import com.ewecarreira.pedidos.domain.entity.Cliente;
import com.ewecarreira.pedidos.domain.entity.Endereco;
import com.ewecarreira.pedidos.domain.enums.Perfil;
import com.ewecarreira.pedidos.domain.enums.TipoCliente;
import com.ewecarreira.pedidos.domain.repository.ClienteRepository;
import com.ewecarreira.pedidos.domain.repository.EnderecoRepository;
import com.ewecarreira.pedidos.exception.AuthorizationException;
import com.ewecarreira.pedidos.exception.DataIntegratyException;
import com.ewecarreira.pedidos.exception.ObjectNotFoundException;
import com.ewecarreira.pedidos.rest.dto.ClienteDto;
import com.ewecarreira.pedidos.rest.dto.ClienteNewDto;
import com.ewecarreira.pedidos.security.UserSS;

@Service
public class ClienteService {

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private EnderecoRepository enderecoRepository;

    @Autowired
    private S3Service s3Service;

    @Autowired
    private ImageService imageService;

    @Value("${img.prefix.client.profile}")
    private String prefix;

    @Value("${img.profile.size}")
    private Integer size;

    public Cliente find(Integer id) {

        UserSS user = UserService.authenticated();

        if (user == null || !user.hasRole(Perfil.ADMIN) && !id.equals(user.getId())) {
            throw new AuthorizationException("Acesso negado");
        }

        Optional<Cliente> cliente = clienteRepository.findById(id);
        return cliente.orElseThrow(() -> new ObjectNotFoundException("Objeto n??o encontrado! Id: " + id +
                ", Tipo: " + Cliente.class.getName()));
    }

    @Transactional
    public Cliente insert(Cliente cliente) {
        cliente.setId(null);
        Cliente clienteSaved = clienteRepository.save(cliente);
        enderecoRepository.save(clienteSaved.getEnderecos().get(0));
        return clienteSaved;
    }

    public Cliente update(Cliente cliente) {
        Cliente clienteToUpdate = find(cliente.getId());
        updateData(clienteToUpdate, cliente);
        return clienteRepository.save(clienteToUpdate);
    }

    public void delete(Integer id) {
        find(id);
        try {
            clienteRepository.deleteById(id);
        } catch (DataIntegrityViolationException e) {
            throw new DataIntegratyException("N??o ?? poss??vel excluir um Cliente que possui pedidos relacionados.");
        }
    }

    public List<Cliente> findAll() {
        return clienteRepository.findAll();
    }

    public Page<Cliente> findPage(Integer page, Integer linesPerPage, String orderBy, String direction) {
        PageRequest pageRequest = PageRequest.of(page, linesPerPage, Sort.Direction.valueOf(direction), orderBy);
        return clienteRepository.findAll(pageRequest);
    }

    public Cliente fromDTO(ClienteDto clienteDto) {
        return new Cliente(clienteDto.getId(), clienteDto.getNome(), clienteDto.getEmail(), null, null, null);
    }

    public Cliente fromDTO(ClienteNewDto clienteNewDto) {
        Cliente cliente = new Cliente(null,
                clienteNewDto.getNome(),
                clienteNewDto.getEmail(),
                clienteNewDto.getCpfCnpj(),
                TipoCliente.toEnum(clienteNewDto.getTipo()),
                bCryptPasswordEncoder.encode(clienteNewDto.getSenha()));
        Cidade cidade = new Cidade(clienteNewDto.getCidadeId(), null, null);
        Endereco endereco = new Endereco(null,
                clienteNewDto.getLogradouro(),
                clienteNewDto.getNumero(),
                clienteNewDto.getComplemento(),
                clienteNewDto.getBairro(),
                clienteNewDto.getCep(),
                cliente,
                cidade);
        cliente.getEnderecos().add(endereco);
        cliente.getTelefones().add(clienteNewDto.getTelefone1());
        if (clienteNewDto.getTelefone2() != null) {
            cliente.getTelefones().add(clienteNewDto.getTelefone2());
        }
        if (clienteNewDto.getTelefone3() != null) {
            cliente.getTelefones().add(clienteNewDto.getTelefone3());
        }
        return cliente;
    }

    private void updateData(Cliente clienteToUpdate, Cliente cliente) {
        clienteToUpdate.setNome(cliente.getNome());
        clienteToUpdate.setEmail(cliente.getEmail());
    }

    public URI uploadProfilePicture(MultipartFile multipartFile) {
        UserSS user = UserService.authenticated();
        if (user == null) {
            throw new AuthorizationException("Acesso negado");
        }

        BufferedImage jpgImage = imageService.getJpgImageFromFile(multipartFile);
        jpgImage = imageService.cropSquare(jpgImage);
        jpgImage = imageService.resize(jpgImage, size);

        String fileName = prefix + user.getId() + ".jpg";

        return s3Service.uploadFile(imageService.getInputStream(jpgImage, "jpg"), fileName, "image");
    }
}
