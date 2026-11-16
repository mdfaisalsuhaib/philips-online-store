package com.philips.onlinestore.repository;

import com.philips.onlinestore.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
