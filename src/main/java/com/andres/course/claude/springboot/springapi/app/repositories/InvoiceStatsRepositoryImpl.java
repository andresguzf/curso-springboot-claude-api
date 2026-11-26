package com.andres.course.claude.springboot.springapi.app.repositories;

import com.andres.course.claude.springboot.springapi.app.models.Customer;
import com.andres.course.claude.springboot.springapi.app.models.Invoice;
import com.andres.course.claude.springboot.springapi.app.models.InvoiceStatus;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceStatsViews.CountTotalView;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceStatsViews.CustomerTotalView;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceStatsViews.MonthStatusTotalView;
import com.andres.course.claude.springboot.springapi.app.repositories.InvoiceStatsViews.StatusTotalView;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.LocalDateField;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Implementación del fragmento {@link InvoiceStatsRepository} con Criteria API (Spring Data la encuentra
 * por el sufijo Impl). Cada consulta combina el filtro (Specification) con sus propias condiciones y
 * agrupa en la base. Criteria genera HQL, así que el @SQLRestriction de Invoice excluye las eliminadas.
 */
class InvoiceStatsRepositoryImpl implements InvoiceStatsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<StatusTotalView> totalsByStatus(Specification<Invoice> filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<StatusTotalView> query = cb.createQuery(StatusTotalView.class);
        Root<Invoice> invoice = query.from(Invoice.class);
        query.select(cb.construct(StatusTotalView.class, invoice.get("status"), cb.count(invoice), sumOfTotal(cb, invoice)))
                .where(filter(filter, invoice, query, cb))
                .groupBy(invoice.get("status"));
        return entityManager.createQuery(query).getResultList();
    }

    @Override
    public List<MonthStatusTotalView> totalsByMonth(Specification<Invoice> filter, LocalDate from, LocalDate to,
                                                    Collection<InvoiceStatus> statuses) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<MonthStatusTotalView> query = cb.createQuery(MonthStatusTotalView.class);
        Root<Invoice> invoice = query.from(Invoice.class);
        Expression<LocalDate> issueDate = invoice.get("issueDate");
        Expression<Integer> year = cb.extract(LocalDateField.YEAR, issueDate);
        Expression<Integer> month = cb.extract(LocalDateField.MONTH, issueDate);
        query.select(cb.construct(MonthStatusTotalView.class, year, month, invoice.get("status"), sumOfTotal(cb, invoice)))
                .where(filter(filter, invoice, query, cb),
                        cb.between(issueDate, from, to),
                        invoice.get("status").in(statuses))
                .groupBy(year, month, invoice.get("status"));
        return entityManager.createQuery(query).getResultList();
    }

    @Override
    public CountTotalView dueBefore(Specification<Invoice> filter, InvoiceStatus status, LocalDate today) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<CountTotalView> query = cb.createQuery(CountTotalView.class);
        Root<Invoice> invoice = query.from(Invoice.class);
        query.select(cb.construct(CountTotalView.class, cb.count(invoice), sumOfTotal(cb, invoice)))
                .where(filter(filter, invoice, query, cb),
                        cb.equal(invoice.get("status"), status),
                        cb.lessThan(invoice.get("dueDate"), today));
        return entityManager.createQuery(query).getSingleResult();
    }

    @Override
    public List<CustomerTotalView> topCustomers(Specification<Invoice> filter, Collection<InvoiceStatus> statuses,
                                                int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<CustomerTotalView> query = cb.createQuery(CustomerTotalView.class);
        Root<Invoice> invoice = query.from(Invoice.class);
        Join<Invoice, Customer> customer = invoice.join("customer");
        Expression<BigDecimal> total = sumOfTotal(cb, invoice);
        query.select(cb.construct(CustomerTotalView.class, customer.get("id"), customer.get("name"),
                        customer.get("lastName"), cb.count(invoice), total))
                .where(filter(filter, invoice, query, cb), invoice.get("status").in(statuses))
                .groupBy(customer.get("id"), customer.get("name"), customer.get("lastName"))
                .orderBy(cb.desc(total), cb.asc(customer.get("id")));
        return entityManager.createQuery(query).setMaxResults(limit).getResultList();
    }

    // sum(total), o 0 si no hay filas (sum de nada es null).
    private static Expression<BigDecimal> sumOfTotal(CriteriaBuilder cb, Root<Invoice> invoice) {
        return cb.coalesce(cb.sum(invoice.get("total")), BigDecimal.ZERO);
    }

    // El filtro como predicado; sin filtro (o si la Specification no restringe nada), verdadero.
    private static Predicate filter(Specification<Invoice> filter, Root<Invoice> invoice, CriteriaQuery<?> query,
                                    CriteriaBuilder cb) {
        Predicate predicate = filter == null ? null : filter.toPredicate(invoice, query, cb);
        return predicate == null ? cb.conjunction() : predicate;
    }
}
