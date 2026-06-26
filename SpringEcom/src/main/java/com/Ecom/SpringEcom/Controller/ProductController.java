package com.Ecom.SpringEcom.Controller;


import com.Ecom.SpringEcom.models.Product;
import com.Ecom.SpringEcom.services.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.ListResourceBundle;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class ProductController {
    @Autowired
    private ProductService productService;
    @GetMapping("/products")
    public ResponseEntity<List<Product>>getProducts()
    {
        return new ResponseEntity<>(productService.getAllproducts(), HttpStatus.OK) ;
    }

    @GetMapping("/product/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable int id){

        Product product = productService.getProductById(id);

        if(product != null)
            return new ResponseEntity<>(product,HttpStatus.OK);
        else
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @GetMapping("product/{productId}/image")
    public ResponseEntity<byte[]>getImageByProductId(@PathVariable int productId){
        Product product = productService.getProductById(productId);
        return new ResponseEntity<>(product.getImageData(),HttpStatus.OK);
    }

    @PostMapping("/product")
    public ResponseEntity<?>addProduct(@RequestPart Product product,@RequestPart MultipartFile imageFile){
        Product saveProduct = null;
        try {
            saveProduct= productService.addProduct(product,imageFile);
            return new ResponseEntity<>(saveProduct,HttpStatus.CREATED);
        }
        catch (IOException e) {
           return new ResponseEntity<>(e.getMessage(),HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/product/{id}")
    public ResponseEntity<String> updateProduct(@PathVariable int id,@RequestPart Product product ,@RequestPart MultipartFile imageFile){
        Product updatedProduct = null;
        try{
            updatedProduct = productService.addProduct(product,imageFile);
            return new ResponseEntity<>("updated",HttpStatus.OK);
        } catch (IOException e) {
            return new ResponseEntity<>(e.getMessage(),HttpStatus.BAD_REQUEST);
        }

    }

    @DeleteMapping("product/{id}")
    public ResponseEntity<String>deleteProduct(@PathVariable int id){
        Product product = productService.getProductById(id);
        if(product != null){
            productService.deleteProduct(id);
            return new ResponseEntity<>("Deleted",HttpStatus.OK);
        }
        else
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @GetMapping("/product/search")
    public ResponseEntity<List<Product>> searchProducts(@RequestParam String keyword){
        List<Product> products = productService.searchProducts(keyword);
        System.out.println("searching with:-" +keyword);
        return new ResponseEntity<>(products,HttpStatus.OK);
    }
}
