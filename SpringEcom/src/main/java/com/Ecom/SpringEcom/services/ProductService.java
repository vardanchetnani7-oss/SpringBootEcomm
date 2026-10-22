package com.Ecom.SpringEcom.services;

import com.Ecom.SpringEcom.exceptions.ResourceNotFoundException;
import com.Ecom.SpringEcom.models.Product;
import com.Ecom.SpringEcom.repository.ProductRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
@Service
public class ProductService {
    @Autowired
    private ProductRepo productrepository;

    @Cacheable(value = "products", key = "'all'")
    public List<Product> getAllproducts() {
        return productrepository.findAll();
    }

    @Cacheable(value = "products", key = "#id")
    public Product getProductById(int id)  {
        return productrepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "productSearch", allEntries = true)
    })
    public Product addProduct(Product product , MultipartFile image) throws IOException {

        product.setImageName(image.getOriginalFilename());
        product.setImageType(image.getContentType());
        product.setImageData(image.getBytes());
        return productrepository.save(product);
    }

    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "productSearch", allEntries = true)
    })
    public void deleteProduct(int id)  {
        if (!productrepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }
        productrepository.deleteById(id);
    }

    @Cacheable(value = "productSearch", key = "#keyword.toLowerCase()")
    public List<Product> searchProducts(String keyword) {
        return productrepository.searchProduct(keyword);
    }
}
