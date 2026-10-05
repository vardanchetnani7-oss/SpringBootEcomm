package com.Ecom.SpringEcom.services;

import com.Ecom.SpringEcom.models.Order;
import com.Ecom.SpringEcom.models.OrderItem;
import com.Ecom.SpringEcom.models.Product;
import com.Ecom.SpringEcom.models.dtos.OrderItemRequest;
import com.Ecom.SpringEcom.models.dtos.OrderItemResponse;
import com.Ecom.SpringEcom.models.dtos.OrderRequest;
import com.Ecom.SpringEcom.models.dtos.OrderResponse;
import com.Ecom.SpringEcom.repository.OrderRepo;
import com.Ecom.SpringEcom.repository.ProductRepo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private OrderRepo orderRepo;
    private ProductRepo productRepo;

    public OrderResponse placeOrder(OrderRequest request) {

       Order order  = new Order();
       String orderId = "ORD" + UUID.randomUUID().toString().substring(0,8).toUpperCase();
       order.setOrderId(orderId);
       order.setCustomerName(request.customerName());
       order.setEmail(request.email());
       order.setStatus("PLACED");
       order.setOrderDate(LocalDate.now().atStartOfDay());

        List<OrderItem> orderItems = new ArrayList<>();
        for(OrderItemRequest orderItemRequest : request.items()) {
            Product product = productRepo.findById(orderItemRequest.productId())
                    .orElseThrow(() -> new RuntimeException("Product Not Found"));
            product.setStockQuantity(product.getStockQuantity() - orderItemRequest.quantity());
            productRepo.save(product);

            OrderItem orderItem  = OrderItem.builder()

                    .product(product)
                    .quantity(orderItemRequest.quantity())
                    .totalPrice(product.getPrice().multiply(BigDecimal.valueOf(orderItemRequest.quantity())))
                    .order(order)
                    .build();

            orderItems.add(orderItem);
        }
        order.setOrderItems(orderItems);
       Order saveOrder =  orderRepo.save(order);

       List<OrderItemResponse> orderResponses = new ArrayList<>();
       for(OrderItem orderItem : order.getOrderItems()) {
          OrderItemResponse orderItemResponse = new OrderItemResponse(
                  orderItem.getProduct().getName(),
                    orderItem.getQuantity(),
                    orderItem.getTotalPrice()
          );
          orderResponses.add(orderItemResponse);
       }
       OrderResponse orderResponse = new OrderResponse(saveOrder.getOrderId(),
               saveOrder.getCustomerName(),
               saveOrder.getEmail(),
               saveOrder.getStatus(),
               saveOrder.getOrderDate(),
               orderResponses);
        return orderResponse;
    }

    public List<OrderResponse> getAllOrderResponses() {
        List<Order>orders  = orderRepo.findAll();
        List<OrderResponse> orderResponses = new ArrayList<>();
        for(Order order : orders) {
            OrderResponse orderResponse = new OrderResponse(

                    order.getOrderId(),
                    order.getCustomerName(),
                    order.getEmail(),
                    order.getStatus(),
                    order.getOrderDate(),
                    order.getOrderItems().stream().map(orderItem -> new OrderItemResponse(
                            orderItem.getProduct().getName(),
                            orderItem.getQuantity(),
                            orderItem.getTotalPrice()
                    )).toList()
            );
            orderResponses.add(orderResponse);
        }
        return orderResponses;
    }
}
