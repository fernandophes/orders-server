package br.edu.ufersa.cc.sd.services;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ufersa.cc.sd.exceptions.NotFoundException;
import br.edu.ufersa.cc.sd.models.Order;
import lombok.Getter;
import lombok.Setter;

public class CacheService {

    @Getter
    private class Metadata<T> {
        @Setter
        private T item;

        private final LocalDateTime firstUse;
        private LocalDateTime lastUse;
        private Integer uses;
        private Integer position;

        public Metadata(final T item) {
            this.item = item;
            this.firstUse = LocalDateTime.now();
            this.lastUse = LocalDateTime.now();
            this.uses = 0;
            this.position = nextPosition++;
        }

        public T getItemAndRegister() {
            LOG.info("Registrando uso do item");
            uses++;
            lastUse = LocalDateTime.now();
            return item;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(CacheService.class.getSimpleName());

    private static final Integer CAPACITY = 30;
    private final Map<Long, Metadata<Order>> cache = new HashMap<>(CAPACITY);

    private Integer nextPosition = 1;
    private Integer hits = 0;
    private Integer misses = 0;

    public Order find(final Long code, final Supplier<Order> redirectCallback) {
        final Order order;

        if (cache.containsKey(code)) {
            LOG.info("Ordem de código {} encontrada no cache", code);
            hits++;
            order = cache.get(code).getItemAndRegister();
        } else {
            LOG.warn("Ordem de código {} NÃO encontrada no cache", code);
            order = tryToGetFromCallback(code, redirectCallback);
        }

        logCacheStatus();
        return order;
    }

    public void update(final Order order) {
        final var code = order.getCode();

        if (cache.containsKey(code)) {
            // Se a ordem editada estiver em cache...
            LOG.info("Ordem de código {} encontrada no cache", code);
            hits++;
            final var cached = cache.get(code);

            // ... ela será substituída pela nova, na mesma posição em que está...
            cached.setItem(order);
        } else {
            // Se não estive em cache...
            LOG.warn("Ordem de código {} NÃO encontrada no cache", code);
            misses++;

            // ... ela será adicionada ao cache na posição mais recente
            addToCache(order);
        }

        logCacheStatus();
    }

    public void delete(final Order order) {
        removeFromCache(order);
    }

    private Order tryToGetFromCallback(final Long code, final Supplier<Order> redirectCallback) {
        try {
            final var result = redirectCallback.get();
            addToCache(result);
            misses++;
            return cache.get(code).getItemAndRegister();
        } catch (final NotFoundException e) {
            return null;
        }
    }

    private void logCacheStatus() {
        final var builder = new StringBuilder();
        builder.append("CACHE: {} hits, {} misses\n");

        cache.values().stream()
                .sorted((a, b) -> a.getPosition().compareTo(b.getPosition()))
                .forEachOrdered(metadata -> builder.append("[").append(metadata.getPosition()).append("ª #")
                        .append(metadata.getItem().getCode()).append(" ")
                        .append(metadata.uses).append("x] "));

        for (int i = cache.size(); i <= CAPACITY; i++) {
            builder.append("[]");
        }

        final var prompt = builder.toString();

        LOG.info(prompt, hits, misses);
    }

    private void addToCache(final Order order) {
        LOG.info("Preparando cache para receber a ordem de código {}", order.getCode());

        if (cache.size() >= CAPACITY) {
            LOG.warn("Cache lotado");
            removeOneFromCache();
        }

        LOG.info("Adicionando ordem de código {} ao cache", order.getCode());
        cache.put(order.getCode(), new Metadata<>(order));
    }

    private void removeOneFromCache() {
        LOG.info("Escolhendo ordem para remover do cache...");

        final var orderToRemove = chooseByFifo();
        removeFromCache(orderToRemove);
    }

    private Order chooseByFifo() {
        return cache.values().stream()
                .min((a, b) -> a.getPosition().compareTo(b.getPosition()))
                .orElseThrow().getItem();
    }

    private void removeFromCache(final Order order) {
        LOG.info("Removendo ordem de código {} do cache...", order.getCode());
        cache.remove(order.getCode());
        logCacheStatus();
    }

}
