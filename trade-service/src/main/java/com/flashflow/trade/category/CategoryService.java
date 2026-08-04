package com.flashflow.trade.category;

import java.util.List;

import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryDtos.CategoryView> listPublic() {
        return categoryRepository.findAllByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(CategoryDtos.CategoryView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryDtos.CategoryView> listAdmin() {
        return categoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(CategoryDtos.CategoryView::from)
                .toList();
    }

    @Transactional
    public CategoryDtos.CategoryView create(CategoryDtos.CreateCategoryRequest request) {
        categoryRepository.findByName(request.name().trim()).ifPresent(existing -> {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "分类名称已存在");
        });
        return CategoryDtos.CategoryView.from(categoryRepository.save(
                new CategoryEntity(request.name().trim(), request.sortOrder())));
    }

    @Transactional
    public CategoryDtos.CategoryView changeStatus(
            long categoryId,
            CategoryDtos.ChangeCategoryStatusRequest request
    ) {
        CategoryEntity category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        category.changeStatus(request.active());
        return CategoryDtos.CategoryView.from(category);
    }
}
