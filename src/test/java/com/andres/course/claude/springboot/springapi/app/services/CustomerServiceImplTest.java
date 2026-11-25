package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.dto.CustomerDto;
import com.andres.course.claude.springboot.springapi.app.dto.CustomerFilter;
import com.andres.course.claude.springboot.springapi.app.exceptions.OperationNotAllowedException;
import com.andres.course.claude.springboot.springapi.app.mappers.CustomerMapper;
import com.andres.course.claude.springboot.springapi.app.models.Customer;
import com.andres.course.claude.springboot.springapi.app.repositories.CustomerRepository;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    private CustomerServiceImpl customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerServiceImpl(customerRepository, invoiceRepository, new CustomerMapper());
    }

    private static Customer customer() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setName("Ana");
        customer.setLastName("Pérez");
        customer.setEmail("ana@ejemplo.com");
        customer.setCity("Santiago");
        customer.setBirthDate(LocalDate.of(1990, 5, 20));
        customer.setActive(true);
        customer.setCreatedAt(LocalDateTime.of(2026, 11, 19, 10, 15, 30));
        return customer;
    }

    private static CustomerFilter filter(LocalDate birthFrom, LocalDate birthTo, LocalDate createdFrom, LocalDate createdTo) {
        return new CustomerFilter(null, null, null, birthFrom, birthTo, createdFrom, createdTo);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findPageDevuelveDtosYAgregaElDesempatePorId() {
        when(customerRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(customer()), invocation.getArgument(1), 21));

        Page<CustomerDto> page = customerService.findPage(CustomerFilter.empty(),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "lastName")));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(customerRepository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getSort()).containsExactly(Sort.Order.desc("lastName"), Sort.Order.asc("id"));
        assertThat(page.getTotalElements()).isEqualTo(21);
        assertThat(page.getContent()).singleElement()
                .satisfies(dto -> {
                    assertThat(dto.email()).isEqualTo("ana@ejemplo.com");
                    assertThat(dto.createdAt()).isEqualTo(LocalDateTime.of(2026, 11, 19, 10, 15, 30));
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllSinPaginarOrdenaPorIdSiNoSePideOrden() {
        when(customerRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(customer()));

        List<CustomerDto> customers = customerService.findAll(CustomerFilter.empty(), Sort.unsorted());

        ArgumentCaptor<Sort> captor = ArgumentCaptor.forClass(Sort.class);
        verify(customerRepository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue()).isEqualTo(Sort.by("id"));
        assertThat(customers).hasSize(1);
    }

    @Test
    void rechazaOrdenarPorUnCampoNoPermitido() {
        assertThatThrownBy(() -> customerService.findPage(CustomerFilter.empty(),
                PageRequest.of(0, 10, Sort.by("phone"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("No se puede ordenar por 'phone'");
        verifyNoInteractions(customerRepository);
    }

    @Test
    void rechazaUnRangoDeNacimientoInvertido() {
        CustomerFilter invertido = filter(LocalDate.of(2000, 1, 1), LocalDate.of(1990, 1, 1), null, null);

        assertThatThrownBy(() -> customerService.findPage(invertido, PageRequest.of(0, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'birthFrom'");
        verifyNoInteractions(customerRepository);
    }

    @Test
    void rechazaUnRangoDeAltaInvertidoTambienAlExportar() {
        CustomerFilter invertido = filter(null, null, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 1));

        assertThatThrownBy(() -> customerService.findAll(invertido, Sort.unsorted()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'createdFrom'");
        verifyNoInteractions(customerRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aceptaUnRangoDeUnSoloDia() {
        LocalDate dia = LocalDate.of(2026, 11, 19);
        when(customerRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));

        Page<CustomerDto> page = customerService.findPage(filter(null, null, dia, dia), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void deleteDeUnClienteSinFacturasLoBorra() {
        Customer customer = customer();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(invoiceRepository.countByCustomerId(1L)).thenReturn(0L);

        customerService.delete(1L);

        verify(customerRepository).delete(customer);
    }

    @Test
    void deleteDeUnClienteConFacturasSeRechaza() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer()));
        when(invoiceRepository.countByCustomerId(1L)).thenReturn(2L);

        assertThatThrownBy(() -> customerService.delete(1L))
                .isInstanceOf(OperationNotAllowedException.class)
                .hasMessage("No se puede eliminar el cliente: tiene 2 factura(s)");
        verify(customerRepository, never()).delete(any(Customer.class));
    }
}
