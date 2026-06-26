package com.Ecom.SpringEcom.services;

import com.Ecom.SpringEcom.models.Product;
import com.Ecom.SpringEcom.repository.ProductRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
@Service
public class ProductService {
    @Autowired
    private ProductRepo productrepository;

    public List<Product> getAllproducts() {
        return productrepository.findAll();
    }

    public Product getProductById(int id){
        return productrepository.findById(id).get();
    }

    public Product addProduct(Product product , MultipartFile image) throws IOException {

        product.setImageName(image.getOriginalFilename());
        product.setImageType(image.getContentType());
        product.setImageData(image.getBytes());
        return productrepository.save(product);
    }


    public void deleteProduct(int id) {
        productrepository.deleteById(id);
    }

    public List<Product> searchProducts(String keyword) {
        return productrepository.searchProduct(keyword);
    }
}
