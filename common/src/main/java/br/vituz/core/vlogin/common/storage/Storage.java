package br.vituz.core.vlogin.common.storage;

import br.vituz.core.vlogin.common.model.Account;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A persistência das contas. Todo método pode bloquear, então nenhum deles
 * pode ser chamado da thread principal.
 */
public interface Storage {
    void initialize() throws StorageException;

    Optional<Account> findByName(String name) throws StorageException;

    Optional<Account> findByUniqueId(UUID uniqueId) throws StorageException;

    Optional<Account> findByMojangId(UUID mojangId) throws StorageException;

    int countByAddress(String address) throws StorageException;

    List<Account> findByAddress(String address) throws StorageException;

    List<Account> findAll() throws StorageException;

    int countInactiveBefore(long timestamp) throws StorageException;

    int deleteInactiveBefore(long timestamp) throws StorageException;

    void insert(Account account) throws StorageException;

    /**
     * Insere muitas contas de uma vez, numa transação só.
     *
     * Existe para a migração: com uma conta por commit, copiar dezenas de milhares
     * de registros leva minutos e deixa o banco em estado parcial se o servidor
     * cair no meio. Devolve quantas entraram.
     */
    int insertAll(List<Account> accounts) throws StorageException;

    /** Os nicknames já presentes, em minúsculas. Uma consulta em vez de N. */
    Set<String> names() throws StorageException;

    void update(Account account) throws StorageException;

    void delete(String name) throws StorageException;

    long count() throws StorageException;

    void close();
}
